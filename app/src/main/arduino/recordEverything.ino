
#include <SPI.h>
#include <SD.h>
#include <mcp_can.h>

#define SPI_CS_PIN  9
MCP_CAN CAN0(SPI_CS_PIN); 


long unsigned int id;
unsigned char len = 0;
unsigned char rxBuf[8];

/* SD card Settings */
char fileName[20];
File dataFile;

// id(3), len(1), data(max 8) = 3+1+8 = 12 bytes
byte capsule[12];

uint32_t flushTime;

void setup(){
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

  // create new log file & append header
  dataFile = SD.open(fileName, FILE_WRITE);
  

  boolean canInit = false;

  while (!canInit) {
    // 500KBPS : https://www.racelogic.co.uk/_downloads/vbox/Vehicles/Other/Docs/Tesla-Model%203.pdf
    // does not work : 8MHz nor 20MHz nor 1MBPS
    if (CAN0.begin(MCP_ANY, CAN_500KBPS, MCP_16MHZ) == CAN_OK) {
      canInit = true;
    }
    else {
      dataFile.println("MCP2515 can not be Initialized");
      dataFile.flush();
      delay(1000);
    }
  }

  // listen bus only
  CAN0.setMode(MCP_LISTENONLY);      

  dataFile.println("data format : id (3 bytes), data len (1 bytes), data (0-8 bytes)");
  dataFile.flush();
}

void loop()
{
  while(CAN_OK == CAN0.readMsgBuf(&id, &len, rxBuf)) {
      // tesla use only 11 bits, max id see : 0x07FF (2047)
      capsule[0] = (byte)((id >> 16) & 0xFF);
      capsule[1] = (byte)((id >> 8) & 0xFF);
      capsule[2] = (byte)(id & 0xFF);

      // data len
      capsule[3] = len;

      // data
      for (int i=0; i<len; i++) {
        capsule[4 + i] = rxBuf[i];
      }
    
      // send to internal buffer
      dataFile.write(capsule, 4 + len);

      // write on sd card every 10 sec max
      uint32_t now = millis();
      if (now - flushTime > 10000) {
          dataFile.flush();
          flushTime = now;
      }
  }
}