#include <CurieBLE.h>
#include "CurieIMU.h"
#include <Wire.h>
#include "rgb_lcd.h"

rgb_lcd lcd;

BLEService MeraGlove("0000180F-0000-1000-8000-00805f9b34fb"); // BLE LED Service
BLEUnsignedCharCharacteristic DumbleWeight("00002A19-0000-1000-8000-00805f9b34fb", BLERead | BLENotify);
BLEUnsignedCharCharacteristic GloveOrientation("00002A40-0000-1000-8000-00805f9b34fb", BLERead | BLENotify);
BLEUnsignedCharCharacteristic Count("00002A80-0000-1000-8000-00805f9b34fb", BLERead | BLENotify);

const int colorR = 127;
const int colorG = 0;
const int colorB = 127;
int freeweights = 0;

void setup() {
  
    Serial.begin(9600);
    Serial.println("setup()");
    Serial.println("attribute table constructed");
    
    // Initialize MeraGlove BLE Service and Start Advertisting
    BLE.setLocalName("DevPost MeraGlove");
    BLE.begin();
    BLE.setAdvertisedService(MeraGlove);
    MeraGlove.addCharacteristic(GloveOrientation);
    MeraGlove.addCharacteristic(DumbleWeight);
    MeraGlove.addCharacteristic(Count);
    BLE.addService(MeraGlove);
    BLE.setAdvertisedServiceUuid(MeraGlove.uuid());
    BLE.advertise();
    
    
    Serial.println("advertising");
    Serial.println("Mera Glove");
    Serial.println("Initializing IMU device...");
    
    //Initialize Accelerometer and Set Accelerometer Range to +/-2g
    CurieIMU.begin();
    CurieIMU.setAccelerometerRange(2);  

  //Using GPIO 6 for Buzzer Output
    pinMode(6, OUTPUT);
    digitalWrite(6, LOW);
  
    //Initialize LCD Screen 
    lcd.begin(16, 2);
    lcd.setRGB(colorR, colorG, colorB);
    lcd.print("DevPost!");

}


/*
 * In the loop method, Arduino 101 waits for Android Device to connect to it. Once connected, it starts reading the 
 * accelerometer data and weight information and sends it as notifications to the Smartphone. 
 * 
 */



void loop() 

{
  // listen for BLE peripherals to connect:
  String orientationString; // string for printing description of orientation
  BLEDevice central = BLE.central();
  int orientation = 00;
  float ax, ay, az;  
  int count = 0; 
  String orient = "";
  
  
  // if a central is connected to peripheral:
  if (central) 
  {  
    Serial.print("Connected to central: ");
    Serial.println(central.address());
    lcd.print("Connected to Mera Glove!");
 
    // while the central is still connected to peripheral:
    while (central.connected()) {

      CurieIMU.readAccelerometerScaled(ax, ay, az);

    /*
     * The following if else statement was used to determine if the weight is lifted upwards or is it being pushed downards.
     * On the glove, the Arduino 101 was placed on the back side. Thus, the Z axis is always negative. 
     * When the weight was lifted upwards, the X-Axis Orientation will rise from 0g to almost 1g. The Y-Axis was also used to 
     * determine if the weight was lifted completely for the rep.
     * 
     * orient variable was defined to keep track of the position of the glove. If the glove was already lifted upwards, the rep count was not incremented. 
     * 
     * Orientation 00 was defined for upwards position (weight lifted)
     * Orientation 01 was defined for downward position 
     * 
     * Digital Pin 6 was used for the Buzzer. We were using 1 kHz tone fro 100 msec to provide audible feedback to user when the weight 
     * was properly lifted for each rep.
     * 
     */
  
      if (ax>0.7 && ax>ay && orient != "up")
      {
          GloveOrientation.setValue(00); 
          Serial.println("Up");
          orientation = 00;
          count++;
          lcd.clear();
          lcd.print(count);
          WeightSensor();
          orient = "up";
          tone(6, 1000, 100);
      }
      else if (az< -0.6 && ay < -0.4 && orient != "down")
      {
          GloveOrientation.setValue(01); 
          Serial.println("Down");
          orientation = 01;
          lcd.clear();
          lcd.print(count);
          orient = "down";
          noTone(6);
      }

      GloveOrientation.setValue(orientation); 
      WeightSensor();
      Count.setValue(count);
     
      delay(100); //added delay of 100 ms to reduce the ble notification delay.

    }
    // when the central disconnects, print it out:
    Serial.print(F("Disconnected from central: "));
    Serial.println(central.address());
  }
  
}


/*
 * Weight Sensors is Calibrated for 5 lbs, 10lbs and 20 lbs. 
 * The Sensor is capable of detecting weights up to 100 lbs. 
 * 
 * The Weight Sensor board provides an amplified analog voltage. This voltage is measured by Arduino 101 ADC
 * and the calibrated value is sent as notification for each BLE characteristic transmission.
 */
  
  void WeightSensor() {
  /* Read the current voltage level on the A0 analog input pin.
     This is used here to simulate the charge level of a battery.
  */
  int weight_lifted = analogRead(A0);   
    
  if (weight_lifted < 25)
  {
    freeweights = 0;
  }
  
  else if (weight_lifted > 40 && weight_lifted < 70)
  {

    freeweights = 5;
  }

  else if (weight_lifted > 90 && weight_lifted < 140)
  {
    freeweights = 10;
  }

  else  if (weight_lifted > 170 && weight_lifted < 220)
  {
    freeweights = 20;
  }

  else 
  {
    freeweights = weight_lifted/10; 

  }

   Serial.print("Weight measured is :"); 
   Serial.println(freeweights);
   DumbleWeight.setValue(freeweights);  // and update the battery level characteristic
     
}



