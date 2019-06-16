package edu.pdx.rsurya07.thingsintro;

import android.app.Activity;
import android.os.Bundle;

import com.google.android.things.pio.Gpio;
import com.google.android.things.pio.I2cDevice;
import com.google.android.things.pio.PeripheralManager;
import com.google.firebase.FirebaseApp;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import android.os.Handler;
import android.util.Log;

import java.io.IOException;
import java.util.Date;
import java.sql.Timestamp;

/**
 * @author Surya Ravikumar
 *
 * Description:     This android things app reads and writes to a firebase realtime database.
 *                  It reads PWM value to write to a RGB LED, a 5-bit value to write to a DAC port.
 *                  It writes temperature, 3 ADC values and timestamp to the database, a PWM value based on the read temperature.
 *
 *                  THis app uses I2C to communicate with PIC16F5325 to read and write values to
 *                  ports on the PIC.
 */
public class HomeActivity extends Activity {

    private static final String TAG = HomeActivity.class.getSimpleName();
    private static final String I2C_DEVICE_NAME = "I2C1";
    private static final int I2C_ADDRESS = 0x08;
    private Gpio mLedGpio;
    private I2cDevice mDevice;
    private Handler mHandler = new Handler();
    private Handler adcHandler = new Handler();
    private DatabaseReference mDatabase;
    private int INTERVAL_BETWEEN_READ_MS = 60000;
    private int INTERVAL_BETWEEN_ADC_MS = 1000;
    private boolean I2C_LED = false;

    Timestamp ts;
    Date date;

    /**
     * Method that reads ADC3, ADC4, ADC5 on PIC every second and writes it to the realtime database
     */
    private Runnable readADC = new Runnable() {
        @Override
        public void run() {
            try
            {
                byte adc3_l = mDevice.readRegByte(0x07);    //read lsb of adc3
                int adc3_l_i = (int)adc3_l & 0xff;
                byte adc3_m = mDevice.readRegByte(0x08);    //read msb of adc3
                int adc3_m_i = (int)adc3_m & 0xff;
                int adc3 = (adc3_m_i << 8) + adc3_l_i;         //bit shift read values and add to form a single value

                byte adc4_l = mDevice.readRegByte(0x09);    //read lsb of adc4
                int adc4_l_i = (int)adc4_l & 0xff;
                byte adc4_m = mDevice.readRegByte(0x0a);    //read msb of adc4
                int adc4_m_i = (int)adc4_m & 0xff;
                int adc4 = (adc4_m_i << 8) + adc4_l_i;         //bit shift read values and add to form a single value

                byte adc5_l = mDevice.readRegByte(0x0b);    //read lsb of adc5
                int adc5_l_i = (int)adc5_l & 0xff;
                byte adc5_m = mDevice.readRegByte(0x0c);    //read msb of adc5
                int adc5_m_i = (int)adc5_m & 0xff;
                int adc5 = (adc5_m_i << 8) + adc5_l_i;         //bit shift read values and add to form a single value

               // int dac1 = (int) mDevice.readRegByte(0x04);

               // Log.i(TAG, "DAC read: " + String.valueOf(dac1));
                //Log.i(TAG, "adc3: " + String.valueOf(adc3));
                //Log.i(TAG, "adc4: " + String.valueOf(adc4));
                //Log.i(TAG, "adc5: " + String.valueOf(adc5));

                //write adc values to database
                mDatabase.child("adc3").setValue(adc3);
                mDatabase.child("adc4").setValue(adc4);
                mDatabase.child("adc5").setValue(adc5);

                //get timestamp and write to database
                date = new Date();
                long time = date.getTime();
                ts = new Timestamp(time);

                Log.i(TAG, "time: " + ts.toString());
                mDatabase.child("Timestamp").setValue(ts.toString());

                //toggle led
                I2C_LED = !I2C_LED;
                setLedValue(I2C_LED);

                //callback after 1 second
                adcHandler.postDelayed(readADC, INTERVAL_BETWEEN_ADC_MS);
            }
            catch(IOException e)
            {
                Log.w(TAG, "Unable to access I2C device", e);
                setLedValue(true);
            }
        }
    };

    /**
     * Method that reads the temperatue and updates it to database every minute
     */
    private Runnable readTemp = new Runnable() {
        @Override
        public void run()
        {
            try
            {
                byte lsb = mDevice.readRegByte(0x05);   //read lsb of ada5
                int lsb_i = (int)lsb & 0xff;
                byte msb = mDevice.readRegByte(0x06);   //read msb of ada5
                int msb_i = (int)msb & 0xff;
                int adc = (msb_i << 8) + lsb_i;            //bit shift read bits and add

                double temp = (((adc * 1980)/1024) - 500)/10;   //convert read value to temp in C

                //Log.i(TAG, "lsb: " + String.valueOf(lsb_i));
                //Log.i(TAG, "msb: " + String.valueOf(msb_i));
                //Log.i(TAG, "adc: " + String.valueOf(adc));
                //Log.i(TAG, "temp: " + String.valueOf(temp));

                mDatabase.child("Temp").setValue(temp);     //write temp to database

                //calculate motor speed according to temperature
                byte motorSpeed;

                if(temp > 15 && temp <= 18)
                    motorSpeed = 0x4c;      //30% duty cycle

                else if(temp > 18 && temp <= 22)
                    motorSpeed = 0x7f;      //50% duty cycle

                else if(temp > 22 && temp <= 25)
                    motorSpeed = (byte) (0xb2 & 0xff);  //70% duty cycle

                else if(temp < 15)
                    motorSpeed = (byte) (0xcc & 0xff);  //80% duty cycle

                else
                    motorSpeed = 0x66;  //40% duty cycle

                //write PWM value to register
                mDevice.writeRegByte(0x3, motorSpeed);

                //write PWM to database
                mDatabase.child("pwm3").setValue((int)motorSpeed & 0xff);

                //Log.i(TAG, "pwm3: " + String.valueOf(mDevice.readRegByte(0x3)));

                //callback this function after 1 minute
                mHandler.postDelayed(readTemp, INTERVAL_BETWEEN_READ_MS);
            }
            catch (IOException e)
            {
                Log.w(TAG, "Unable to access I2C device", e);
                setLedValue(true);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.i(TAG, "Starting ButtonActivity");

        //initialize firebase
        FirebaseApp.initializeApp(this);
        mDatabase = FirebaseDatabase.getInstance().getReference();
        getDataInit();

        //get peripherals
        PeripheralManager pioService = PeripheralManager.getInstance();
        try
        {
            //get I2C peripheral
            mDevice = pioService.openI2cDevice(I2C_DEVICE_NAME, I2C_ADDRESS);

            //initialize handlers to read values
            mHandler.post(readTemp);
            adcHandler.post(readADC);
        }
        catch(IOException e)
        {
            Log.w(TAG, "Unable to access I2C device", e);
            setLedValue(true);
        }

        try
        {
            //get GPIO pin for LED
            mLedGpio = pioService.openGpio("BCM6");
            mLedGpio.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
        }
        catch (IOException e)
        {
            Log.e(TAG, "Error configuring GPIO pins", e);
        }
    }

    /**
     * Method that listens for data change in the database
     */
    private void getDataInit() {
        ValueEventListener dataListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {

                //Read values from data base
                String pwm0 = dataSnapshot.child("pwm0").getValue().toString();
                String pwm1 = dataSnapshot.child("pwm1").getValue().toString();
                String pwm2 = dataSnapshot.child("pwm2").getValue().toString();
                String dac1 = dataSnapshot.child("dac1").getValue().toString();

                int pwm0_i = Integer.parseInt(pwm0);
                int pwm1_i = Integer.parseInt(pwm1);
                int pwm2_i = Integer.parseInt(pwm2);
                byte dac1_i = (byte) (Integer.parseInt(dac1) & 0x1f);

                //Log.i(TAG, "PWM0: " + pwm0);
               // Log.i(TAG, "PWM1: " + pwm1);
                //Log.i(TAG, "PWM2: " + pwm2);
                //Log.i(TAG, "DAC1: " + dac1);

                try
                {
                    //write PWM & DAC values to ports through I2C
                    mDevice.writeRegByte(0x0,  (byte) (pwm0_i & 0xff));
                    mDevice.writeRegByte(0x1, (byte) (pwm1_i & 0xff));
                    mDevice.writeRegByte(0x2,  (byte) (pwm2_i & 0xff));
                    mDevice.writeRegByte(0x4, dac1_i);
                }
                catch(IOException e)
                {
                    Log.w(TAG, "Unable to access I2C device", e);
                    setLedValue(true);
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.w(TAG, "onCancelled", databaseError.toException());
            }
        };

        mDatabase.addValueEventListener(dataListener);
    }

    /**
     * Method that writes to a GPIO pin to turn on or off LED
     * @param value true - on, false - off
     */
    private void setLedValue(boolean value) {
        try
        {
            mLedGpio.setValue(value);
        }
        catch (IOException e)
        {
            Log.e(TAG, "Error updating GPIO value", e);
        }
    }

    @Override
    protected void onStop(){
        Log.d(TAG, "onStop called.");

        if (mLedGpio != null) {
            try {
                Log.d(TAG, "Unregistering LED.");
                mLedGpio.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing LED GPIO", e);
            } finally{
                mLedGpio = null;
            }
        }
        super.onStop();
    }
}
