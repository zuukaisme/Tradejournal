#property strict
#property version "1.00"

#include <Trade/Trade.mqh>

input string BridgeHost = "127.0.0.1";
input uint BridgePort = 8765;
input string BridgeToken = "";
input bool AllowLiveTrading = false;
input uint ReconnectSeconds = 3;

CTrade Trade;
int BridgeSocket = INVALID_HANDLE;
bool BridgeAuthenticated = false;
string ReceiveBuffer = "";
ulong LastSnapshot = 0;
ulong LastConnectAttempt = 0;

bool SendLine(const string value)
{
   if(BridgeSocket == INVALID_HANDLE || !SocketIsConnected(BridgeSocket))
      return false;

   uchar bytes[];
   int length = StringToCharArray(value + "\n", bytes, 0, WHOLE_ARRAY, CP_UTF8);
   if(length <= 1)
      return false;

   int sent = SocketSend(BridgeSocket, bytes, length - 1);
   return sent == length - 1;
}

void DisconnectBridge()
{
   if(BridgeSocket != INVALID_HANDLE)
   {
      SocketClose(BridgeSocket);
      BridgeSocket = INVALID_HANDLE;
   }
   ReceiveBuffer = "";
   BridgeAuthenticated = false;
}

bool ConnectBridge()
{
   if(BridgeSocket != INVALID_HANDLE && SocketIsConnected(BridgeSocket))
      return true;

   DisconnectBridge();
   BridgeSocket = SocketCreate();
   if(BridgeSocket == INVALID_HANDLE)
      return false;

   if(!SocketConnect(BridgeSocket, BridgeHost, BridgePort, 500))
   {
      DisconnectBridge();
      return false;
   }

   if(!SendLine("AUTH|" + BridgeToken))
   {
      DisconnectBridge();
      return false;
   }
   return true;
}

ENUM_TIMEFRAMES ParseTimeframe(const string value)
{
   if(value == "M1") return PERIOD_M1;
   if(value == "M2") return PERIOD_M2;
   if(value == "M3") return PERIOD_M3;
   if(value == "M4") return PERIOD_M4;
   if(value == "M5") return PERIOD_M5;
   if(value == "M6") return PERIOD_M6;
   if(value == "M10") return PERIOD_M10;
   if(value == "M12") return PERIOD_M12;
   if(value == "M15") return PERIOD_M15;
   if(value == "M20") return PERIOD_M20;
   if(value == "M30") return PERIOD_M30;
   if(value == "H1") return PERIOD_H1;
   if(value == "H2") return PERIOD_H2;
   if(value == "H3") return PERIOD_H3;
   if(value == "H4") return PERIOD_H4;
   if(value == "H6") return PERIOD_H6;
   if(value == "H8") return PERIOD_H8;
   if(value == "H12") return PERIOD_H12;
   if(value == "D1") return PERIOD_D1;
   if(value == "W1") return PERIOD_W1;
   if(value == "MN1") return PERIOD_MN1;
   return PERIOD_CURRENT;
}

void SendAccountSnapshot()
{
   SendLine("ACCOUNT|" +
      IntegerToString((long)AccountInfoInteger(ACCOUNT_LOGIN)) + "|" +
      AccountInfoString(ACCOUNT_SERVER) + "|" +
      AccountInfoString(ACCOUNT_CURRENCY) + "|" +
      DoubleToString(AccountInfoDouble(ACCOUNT_BALANCE), 2) + "|" +
      DoubleToString(AccountInfoDouble(ACCOUNT_EQUITY), 2) + "|" +
      DoubleToString(AccountInfoDouble(ACCOUNT_MARGIN), 2) + "|" +
      DoubleToString(AccountInfoDouble(ACCOUNT_MARGIN_FREE), 2));

   int total = PositionsTotal();
   for(int index = 0; index < total; index++)
   {
      ulong ticket = PositionGetTicket(index);
      if(ticket == 0) continue;

      SendLine("POSITION|" +
         IntegerToString((long)ticket) + "|" +
         PositionGetString(POSITION_SYMBOL) + "|" +
         IntegerToString((int)PositionGetInteger(POSITION_TYPE)) + "|" +
         DoubleToString(PositionGetDouble(POSITION_VOLUME), 2) + "|" +
         DoubleToString(PositionGetDouble(POSITION_PRICE_OPEN), _Digits) + "|" +
         DoubleToString(PositionGetDouble(POSITION_PROFIT), 2));
   }
   SendLine("SNAPSHOT_END");
}

void SendBars(const string symbol, const string timeframeText, const int requestedCount)
{
   ENUM_TIMEFRAMES timeframe = ParseTimeframe(timeframeText);
   if(timeframe == PERIOD_CURRENT || !SymbolSelect(symbol, true))
   {
      SendLine("ERROR|BARS|Invalid symbol or timeframe");
      return;
   }

   int count = MathMax(1, MathMin(requestedCount, 1000));
   MqlRates rates[];
   ArraySetAsSeries(rates, true);
   int copied = CopyRates(symbol, timeframe, 0, count, rates);
   if(copied <= 0)
   {
      SendLine("ERROR|BARS|No candle data");
      return;
   }

   int digits = (int)SymbolInfoInteger(symbol, SYMBOL_DIGITS);
   for(int index = copied - 1; index >= 0; index--)
   {
      SendLine("BAR|" + symbol + "|" + timeframeText + "|" +
         IntegerToString((long)rates[index].time) + "|" +
         DoubleToString(rates[index].open, digits) + "|" +
         DoubleToString(rates[index].high, digits) + "|" +
         DoubleToString(rates[index].low, digits) + "|" +
         DoubleToString(rates[index].close, digits) + "|" +
         IntegerToString((long)rates[index].tick_volume));
   }
   SendLine("BARS_END|" + symbol + "|" + timeframeText);
}

void HandleCommand(const string line)
{
   string parts[];
   int count = StringSplit(line, '|', parts);
   if(count <= 0) return;

   if(parts[0] == "AUTH_OK")
   {
      if(!BridgeAuthenticated)
      {
         BridgeAuthenticated = true;
         SendLine("HELLO|" + IntegerToString((long)AccountInfoInteger(ACCOUNT_LOGIN)) + "|" +
                  AccountInfoString(ACCOUNT_SERVER) + "|" + AccountInfoString(ACCOUNT_COMPANY));
      }
      return;
   }

   if(parts[0] == "AUTH_FAILED")
   {
      DisconnectBridge();
      return;
   }

   if(!BridgeAuthenticated) return;

   if(parts[0] == "PING")
   {
      SendLine("PONG");
      return;
   }

   if(parts[0] == "BARS" && count >= 4)
   {
      SendBars(parts[1], parts[2], (int)StringToInteger(parts[3]));
      return;
   }

   if((parts[0] == "BUY" || parts[0] == "SELL") && count >= 3)
   {
      if(!AllowLiveTrading)
      {
         SendLine("ERROR|TRADING_DISABLED|Enable AllowLiveTrading in the EA inputs after demo testing.");
         return;
      }

      string symbol = parts[1];
      double volume = StringToDouble(parts[2]);
      bool success = false;

      if(volume > 0 && SymbolSelect(symbol, true))
      {
         if(parts[0] == "BUY")
            success = Trade.Buy(volume, symbol);
         else
            success = Trade.Sell(volume, symbol);
      }

      SendLine("ORDER_RESULT|" + parts[0] + "|" + symbol + "|" +
         (success ? "OK" : "FAILED") + "|" +
         IntegerToString((int)Trade.ResultRetcode()) + "|" +
         Trade.ResultRetcodeDescription());
      return;
   }

   if(parts[0] == "CLOSE" && count >= 2)
   {
      if(!AllowLiveTrading)
      {
         SendLine("ERROR|TRADING_DISABLED|Enable AllowLiveTrading in the EA inputs after demo testing.");
         return;
      }

      ulong ticket = (ulong)StringToInteger(parts[1]);
      bool success = Trade.PositionClose(ticket);
      SendLine("CLOSE_RESULT|" + parts[1] + "|" +
         (success ? "OK" : "FAILED") + "|" +
         IntegerToString((int)Trade.ResultRetcode()));
      return;
   }

   if(parts[0] == "CLOSE_ALL")
   {
      if(!AllowLiveTrading)
      {
         SendLine("ERROR|TRADING_DISABLED|Enable AllowLiveTrading in the EA inputs after demo testing.");
         return;
      }

      int closed = 0;
      int failed = 0;
      for(int index = PositionsTotal() - 1; index >= 0; index--)
      {
         ulong ticket = PositionGetTicket(index);
         if(ticket == 0) continue;
         if(Trade.PositionClose(ticket)) closed++;
         else failed++;
      }
      SendLine("CLOSE_ALL_RESULT|" + IntegerToString(closed) + "|" + IntegerToString(failed));
      return;
   }

   SendLine("ERROR|COMMAND|Unknown command");
}

void ReadCommands()
{
   if(BridgeSocket == INVALID_HANDLE || !SocketIsConnected(BridgeSocket))
      return;

   uint available = SocketIsReadable(BridgeSocket);
   if(available == 0) return;

   uchar bytes[];
   int received = SocketRead(BridgeSocket, bytes, MathMin(available, 8192), 1);
   if(received <= 0)
   {
      DisconnectBridge();
      return;
   }

   ReceiveBuffer += CharArrayToString(bytes, 0, received, CP_UTF8);
   while(true)
   {
      int newline = StringFind(ReceiveBuffer, "\n");
      if(newline < 0) break;

      string line = StringSubstr(ReceiveBuffer, 0, newline);
      ReceiveBuffer = StringSubstr(ReceiveBuffer, newline + 1);
      StringTrimRight(line);
      if(line != "") HandleCommand(line);
   }

   if(StringLen(ReceiveBuffer) > 65536)
      ReceiveBuffer = "";
}

int OnInit()
{
   Trade.SetAsyncMode(false);
   EventSetTimer(1);
   LastConnectAttempt = 0;
   return INIT_SUCCEEDED;
}

void OnTimer()
{
   ulong now = GetTickCount64();

   if(BridgeSocket == INVALID_HANDLE || !SocketIsConnected(BridgeSocket))
   {
      if(now - LastConnectAttempt >= (ulong)ReconnectSeconds * 1000)
      {
         LastConnectAttempt = now;
         ConnectBridge();
      }
      return;
   }

   ReadCommands();
   if(BridgeAuthenticated && now - LastSnapshot >= 1000)
   {
      LastSnapshot = now;
      SendAccountSnapshot();
   }
}

void OnDeinit(const int reason)
{
   EventKillTimer();
   DisconnectBridge();
}
