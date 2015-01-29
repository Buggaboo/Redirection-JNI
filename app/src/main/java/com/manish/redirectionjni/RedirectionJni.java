package com.manish.redirectionjni;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.zip.Inflater;

import android.app.Activity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.os.Bundle;
import android.os.Handler;

/*
 * This application redirects the STDIN & STDOUT of C code segment running in JNI.
 * 
 * It creates 2 named pipes (FIFO), one for input and other for output.
 * 
 * It opens up one end of the pipe in write only mode in native code and other end of the pipe in read only mode in Java code. 
 * The file descriptor in native code is mapped to STDOUT i.e. 1, thereafter any writes to STDOUT in native code, will be 
 * redirected to other end of pipe which can read in Java code.
 * 
 * It opens up one end of the pipe in read only mode in native code and other end of the pipe in write only mode in Java code. 
 * The file descriptor in native code is mapped to STDIN i.e. 0, thereafter any writes to other end of pipe in Java code, will be
 * read by native code using STDIN.
 */
public class RedirectionJni extends Activity {
    Handler mHandler;

    String mOutfile; // This file is used for STDOUT redirection. Java code will read content written by native code using STDOUT.
    String mInfile; // This file is used for STDIN redirection. Java code will write content which will be read by native code using STDIN.
    private EditText mEditText;
    private TextView mTextView;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        File dir = getFilesDir();
        if (!dir.exists()) {
            dir.mkdir();
        }

        mOutfile = this.getFilesDir() + "/out";
        mInfile = this.getFilesDir() + "/in";
        mHandler = new Handler();

        /* Create a TextView and set its content.
         * the text is retrieved by calling a native
         * function.
         */
//        TextView  tv = new TextView(this);
//		tv.setText("Click me to use stdin & stdout");
        setContentView(R.layout.layout);

        new Thread(new Runnable() {

            @Override
            public void run() {
                // TODO Auto-generated method stub
                stringFromJNI(mOutfile, mInfile);
            }

        }).start();


        mTextView = (TextView) findViewById(R.id.textView);
        mTextView.setHint("Tap to read stdio");

        mEditText = (EditText) findViewById(R.id.editText);

        mTextView.setOnClickListener(new View.OnClickListener() {

            /*
             * This thread is used for reading content which will be written by native code using STDOUT.
             */
            @Override
            public void onClick(View v) {
                readSysout();
            }
        });
    }

    private void readSysout() {
        new Thread(new Runnable() {

            @Override
            public void run() {
                BufferedReader in = null;
                try {
                    in = new BufferedReader(new FileReader(mOutfile));
                    while (in.ready()) {
                        final String str = in.readLine();
                        mHandler.post(new Runnable() {

                            @Override
                            public void run() {
                                mTextView.setText(str);
                            }

                        });
                    }
                    in.close();
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

        }).start();
    }

    /* A native method that is implemented by the
     * 'redirection-jni' native library, which is packaged
     * with this application.
     */
    public native String stringFromJNI(String outfile, String infile);


    public boolean onCreateOptionsMenu(final Menu menu) {
        final MenuInflater inflater = this.getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        /*
         * This thread is used for writing content which will be read by native code using STDIN.
         */
        new Thread(new Runnable() {

            @Override
            public void run() {
                // TODO Auto-generated method stub
                BufferedWriter out = null;
                try {
                    // write to sysin proxy
                    out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(mInfile)));
                    String content = mEditText.getText().toString();
                    out.write(content.toCharArray(), 0, content.toCharArray().length);
                    out.flush();
                    out.close();

                    // read data from sysin (proxy) to sysout, simulate the JNI stuff
                    BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
                    String line = null;

                    while ((line = br.readLine()) != null) {
                        System.out.println(line);
                    }

                    /**
                     * Conclusion:
                     *
                     * Writing to the proxy sysin, then reading the same value from sysin proves that sysin redirection works.
                     *
                     * Writing to sysout and intercepting with the proxy, stays open as long as the proxy (initiated in th JNI) stays open.
                     * See line: while (in.ready()) // i.e. not closed (F4 on method)
                     *
                     */

                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

        }).start();
        return super.onOptionsItemSelected(item);
    }

    static {
        System.loadLibrary("redirection-jni");
    }
}
