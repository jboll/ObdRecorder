
#include <SPI.h>
#include <SD.h>
#include <mcp_can.h>

#define SPI_CS_PIN  9
MCP_CAN CAN(SPI_CS_PIN); 


long unsigned int id;
unsigned char len = 0;
unsigned char rxBuf[8];

/* SD card Settings */
char fileName[20];
File dataFile;

// time(4), id(1), len(1), data(max 8) = 4+1+1+8 = 14 bytes
byte capsule[14];

uint32_t flushTime;

void setup() {
  pinMode(12, OUTPUT);      // OPEN THE POWER SUPPLY
  digitalWrite(12, HIGH);
    
  // init sd card
  if (!SD.begin(5)) {
    return;
  }

  // find next log file name
  for (uint8_t i = 1; i < 10000; i++){
    sprintf(fileName, "DATA%d.log", i);
    if (SD.exists(fileName) == false){
      break;
    }
  }

  // create new log file
  dataFile = SD.open(fileName, FILE_WRITE);
  

  // init CAN BUS module
  boolean canInit = false;
  while (!canInit) {
    // 500KBPS : https://www.racelogic.co.uk/_downloads/vbox/Vehicles/Other/Docs/Tesla-Model%203.pdf
    // does not work : 8MHz nor 20MHz nor 1MBPS
    // use MCP_STDEXT for enabling filters (MCP_STD does allows to MCP2515 init)
    if (CAN.begin(MCP_STDEXT, CAN_500KBPS, MCP_16MHZ) == CAN_OK) {
      canInit = true;
    }
    else {
      dataFile.println("MCP2515 can not be Initialized");
      dataFile.flush();
      delay(1000);
    }
  }

  // filter (with the following filters, we can store 654 hours of recording inside a 4 Go file size ! and with a loss between 0.1% and 0.9%)
  CAN.init_Mask(0, 0, 0x07FF0000); // all filter bits enabled
  CAN.init_Filt(0, 0, 0x01320000); // keep messages with id 306 Battery pack voltage
  CAN.init_Filt(1, 0, 0x02640000); // keep messages with id 612 ChargeLineStatus

  CAN.init_Mask(1, 0, 0x07FF0000); // all filter bits enabled
  CAN.init_Filt(2, 0, 0x033A0000); // keep messages with id 826 SOC
  CAN.init_Filt(3, 0, 0x03520000); // keep messages with id 850 BMS
  CAN.init_Filt(4, 0, 0x04010000); // keep messages with id 1025 cells voltage
  CAN.init_Filt(5, 0, 0x03180000); // keep messages with id 792 date

  // listen bus only
  CAN.setMode(MCP_LISTENONLY);      

  // write how the data are written
  dataFile.println("data format : time (4bytes), id (1 byte {0=Unknown, 1=306, 2=612, 3=792, 4=826, 5=850, 6=1025}), data len (1 byte), data (0-8 bytes)");
  dataFile.flush();
}

void loop() {

  // readMsgBuf does a check inside this function so we can call it immediatly
  while(CAN_OK == CAN.readMsgBuf(&id, &len, rxBuf)) {

    // does not allows degenerated messages
    if (len > 0 && len < 9) {

      // time
      uint32_t uptime = millis();
      capsule[0] = (byte)((uptime >> 24) & 0xFF);
      capsule[1] = (byte)((uptime >> 16) & 0xFF);
      capsule[2] = (byte)((uptime >> 8) & 0xFF);
      capsule[3] = (byte)(uptime & 0xFF);

      // message id (compressed : 3 bytes gain by using this mapping function instead of write the full id that took 4 bytes)
      if (id == 306) capsule[4] = 1;       // period of 10 ms, check it first
      else if (id == 612) capsule[4] = 2;  // period of 100 ms
      else if (id == 792) capsule[4] = 3;  // period of 100 ms
      else if (id == 826) capsule[4] = 4;  // period of 1 second
      else if (id == 850) capsule[4] = 5;  // period of 1 second
      else if (id == 1025) capsule[4] = 6; // period of 1.6 seconds
      else capsule[4] = 0;                 // error

      // data len
      capsule[5] = len;

      // data
      for (int i=0; i<len; i++) {
        capsule[6 + i] = rxBuf[i];
      }
    
      // send to internal buffer
      dataFile.write(capsule, 6 + len);

      // write on sd card every 10 sec max
      if (uptime - flushTime > 10000) {
          dataFile.flush();
          flushTime = uptime;
      }
    }
  }
}