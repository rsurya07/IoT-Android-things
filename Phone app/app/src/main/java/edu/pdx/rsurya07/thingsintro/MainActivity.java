/**
 * @author Dhakshayini Koppad 
 *
 * Description:     This android things app reads and writes to a firebase realtime database.
 *                  It writes PWM value to write to a RGB LED(pi), a 5-bit value to write to a DAC port(pi).
 *                  It read temperature, 3 ADC values and timestamp to the database, and the motor speed.
 *
 *  My commits were getting pushed my public repository as Android studio is set up with dkoppad as the default.
 *  link to my repo:
 *              https://github.com/dkoppad/androidThingsapp.git
 *              https://github.com/dkoppad/project3.git
 */


package edu.pdx.rsurya07.thingsintro;

import android.os.Bundle;

import android.os.Handler;
import android.support.v7.app.AppCompatActivity;


import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;


public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final String RED_INDEX = "red";
    private static final String BLUE_INDEX = "blue";
    private static final String GREEN_INDEX = "green";
    private static final String DAC = "DAC";

    private SeekBar mRedControl;
    private SeekBar mBlueControl;
    private SeekBar mGreenControl;
    private ProgressBar mMotorSpeed;
    private TextView mMSpeed;
    private TextView madc1;
    private TextView madc2;
    private TextView madc3;

    private TextView mTemp;
    private EditText mdac;
    private TextView mTimestamp;
    private DatabaseReference mDatabase;
    private Handler handler = new Handler();

    private int pwm0 = 0;
    private int pwm1 = 0;
    private int pwm2 = 0;
    private int dac;
    
    /* Variables to store values read from Firebase */
    private String pwm3_iot = String.valueOf(0);            
    private String adc1_iot = String.valueOf(0);
    private String adc2_iot = String.valueOf(0);
    private String adc3_iot = String.valueOf(0);
    private String Temp_iot = String.valueOf(0);
    private String ts;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "OnCreate called");
        setContentView(R.layout.activity_main);

        if (savedInstanceState != null) {
            pwm0 = savedInstanceState.getInt(RED_INDEX, 0);
            pwm1 = savedInstanceState.getInt(GREEN_INDEX, 0);
            pwm2 = savedInstanceState.getInt(BLUE_INDEX, 0);
            dac = savedInstanceState.getInt(DAC, 0);
        }
        /*
        Initial Values

        Set values initially to zero when the app starts up for the first time
        so that the LEDs are not lit.
         */
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        database.getReference().child("pwm0").setValue(pwm0);
        database.getReference().child("pwm1").setValue(pwm1);
        database.getReference().child("pwm2").setValue(pwm2);
        database.getReference().child("dac1").setValue(dac);

        mDatabase = FirebaseDatabase.getInstance().getReference();
        
        /* call to get the data from Firebase */
        getDataInit();
        mMotorSpeed = (ProgressBar) findViewById(R.id.progressBar);
        mMSpeed = (TextView) findViewById(R.id.motorSpeed);

        madc1 = (TextView) findViewById(R.id.adc1);
        madc1.setText(adc1_iot);

        madc2 = (TextView) findViewById(R.id.adc2);
        madc2.setText(adc2_iot);

        madc3 = (TextView) findViewById(R.id.adc3);
        madc3.setText(adc3_iot);

        mTemp = (TextView) findViewById(R.id.temp);
        mTemp.setText(Temp_iot);

        mTimestamp = (TextView) findViewById(R.id.time);

        mdac = (EditText) findViewById(R.id.dac);
        
        /* Method to see user input for the DAC Value.
        *  the value range is between 0 to 31 so vhecking if the user input is within range and sets the 
        * value to the database.
        * in case its more than acceptable range a tost informs user that data is out of range 
        */
        mdac.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dac = Integer.parseInt(mdac.getText().toString());
                FirebaseDatabase database = FirebaseDatabase.getInstance();
                if((dac >= 0) && (dac <= 31)) {
                    Toast.makeText(MainActivity.this, "DAC: " + dac, Toast.LENGTH_SHORT).show();
                    database.getReference().child("dac1").setValue(dac);

                }
                else {
                    Toast.makeText(MainActivity.this, "Out of range input!" , Toast.LENGTH_SHORT).show();
                }
            }
        });

    /*
        Red Control Seek Bar
         */
        mRedControl = (SeekBar) findViewById(R.id.redbar);
        mRedControl.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progressValue, boolean fromUser) {
                pwm0 = progressValue*255/100;
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                Toast.makeText(MainActivity.this, "Intensity:" + pwm0, Toast.LENGTH_SHORT).show();
                FirebaseDatabase database = FirebaseDatabase.getInstance();
                database.getReference().child("pwm0").setValue(pwm0);
            }
        });

        /*
        Green Control Seek Bar
         */
        mGreenControl = (SeekBar) findViewById(R.id.greenbar);
        mGreenControl.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progressValue, boolean fromUser) {
                pwm2 = progressValue*255/100;
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                Toast.makeText(MainActivity.this, "Intensity:" + pwm2, Toast.LENGTH_SHORT).show();
                FirebaseDatabase database = FirebaseDatabase.getInstance();
                database.getReference().child("pwm2").setValue(pwm2);
            }
        });

        /*
        Blue Control Seek Bar
         */
        mBlueControl = (SeekBar) findViewById(R.id.bluebar);
        mBlueControl.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progressValue, boolean fromUser) {
                pwm1 = progressValue*255/100;
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                Toast.makeText(MainActivity.this, "Intensity:" + pwm1, Toast.LENGTH_SHORT).show();
                FirebaseDatabase database = FirebaseDatabase.getInstance();
                database.getReference().child("pwm1").setValue(pwm1);
            }
        });
    }


    /*
   Save the current values of the LEDs so when the screen rotates,
   the information is saved.
    */
    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        Log.i(TAG, "OnSaveInstanceState");
        savedInstanceState.putInt(RED_INDEX, pwm0);
        savedInstanceState.putInt(GREEN_INDEX, pwm1);
        savedInstanceState.putInt(BLUE_INDEX, pwm2);
        savedInstanceState.putInt(DAC, dac);
    }
    
    /* getDataInit() function acess Firebase and reads the ADC, Motor Speed, timestamp and Temp to 
    * to display on the app*
    */

    private void getDataInit() {
        ValueEventListener dataListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                //Get data from database
                pwm3_iot = dataSnapshot.child("pwm3").getValue().toString();
                adc1_iot = dataSnapshot.child("adc3").getValue().toString();
                adc2_iot = dataSnapshot.child("adc4").getValue().toString();
                adc3_iot = dataSnapshot.child("adc5").getValue().toString();
                Temp_iot = dataSnapshot.child("Temp").getValue().toString();
                ts = dataSnapshot.child("Timestamp").getValue().toString();

                mTimestamp.setText("Last response from connect device: \n" + ts);
                mTemp.setText("Temperature: " + Temp_iot + "C");

                madc1.setText("ADC3\n  "+adc1_iot);
                madc2.setText("ADC4\n  "+adc2_iot);
                madc3.setText("ADC5\n  "+adc3_iot);

                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        mMotorSpeed.setProgress(Integer.parseInt(pwm3_iot)*100/255);
                        mMSpeed.setText(Integer.parseInt(pwm3_iot)*100/255 + "%");
                    }
                });
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        };
        mDatabase.addValueEventListener(dataListener);
    }
}
