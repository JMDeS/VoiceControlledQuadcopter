package com.jmdes.myapplication;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.speech.RecognizerIntent;
import android.util.AttributeSet;
import android.util.Log;
import android.view.WindowManager;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.SurfaceHolder.Callback;

//import com.test.BTClient.BTClient;
//RunnableThe interface method creates an anonymous class
@SuppressLint("NewApi")
public class MySurfaceView extends SurfaceView  implements Callback, Runnable {

    private final static String TAG = MySurfaceView.class.getSimpleName();

	private Thread th;
	private SurfaceHolder sfh;
	private Canvas canvas;
	private Paint paint;
	private boolean flag;

	private float LEFT_CENTERX;
	private float LEFT_CENTERY;
	private float RIGHT_CENTERX;
	private float RIGHT_CENTERY;
	
	private float BACK_RECT_SIZE;

	//Left and right rocker on white background
	private float BackRectLeft,BackRectTop,BackRectRight,BackRectButtom;
    private float BackRectLeft2,BackRectTop2,BackRectRight2,BackRectButtom2;

	//The X and Y coordinates of the joystick and the radius of the joystick
	public float SmallRockerCircleX;
	public float SmallRockerCircleY;
	private float SmallRockerCircleR;
	
	//Fixed X, Y coordinates and radius of the background of the rocker
    private float RockerCircleX;
    private float RockerCircleY;
    private float RockerCircleR;
	private float RockerCircleX2;
	private float RockerCircleY2;
	private float RockerCircleR2;
    private RectF logoLocation;

	//The X and Y coordinates of the joystick and the radius of the joystick
	public float SmallRockerCircleX2;
	public float SmallRockerCircleY2;
	private float SmallRockerCircleR2;

	public float leftTouchStartX,leftTouchStartY,rightTouchStartX,rightTouchStartY;

	static final int YAW_STOP_CONTROL=0;
//    public int altCtrlMode=0;
    public int altCtrlMode=1;

	public boolean leftTouching=false,rightTouching=false;
	private int leftTouchIndex=0,rightTouchIndex=0;

	public boolean touchReadyToSend=false;
	
	public MySurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        Log.i(TAG, "MySurfaceView");
        sfh = this.getHolder();
        sfh.addCallback(this);
        paint = new Paint();
        paint.setAntiAlias(true);
        setFocusable(true);
        setFocusableInTouchMode(true);
    }

	public void surfaceCreated(SurfaceHolder holder) { 
		th = new Thread(this);
		flag = true;
		th.start();
	}

	//Stick Size init
    //screenWidth For the entire mobile phone screen pixel width, for example, Huawei Mate 7 is 1920
    //HeightFor the rocker outside the square box side length,
    // the default is half the height of mobile phone screen pixels, such as Huawei Mate 7's screenHeight
    //1080，The Height value is 1080/2=540
	private void stickSizeInit(int screenWidth,int Height)
	{
        LEFT_CENTERX = Height / 2;
        LEFT_CENTERY = Height / 2;
        RIGHT_CENTERX = screenWidth - LEFT_CENTERX;
        RIGHT_CENTERY = Height / 2;
        BACK_RECT_SIZE = Height / 2 - 20;

        // Left circular rocker returns to
        // neutral X, Y, coordinates and radius
        RockerCircleX = LEFT_CENTERX;
        RockerCircleY = LEFT_CENTERY;
        RockerCircleR = (float) ((BackRectRight - BackRectLeft) / 2 * 1.41421);

        // Right circular rocker returns to
        // neutral X, Y, coordinates and radius
        RockerCircleX2 = RIGHT_CENTERX;
        RockerCircleY2 = RIGHT_CENTERX;
        RockerCircleR2 = RockerCircleR;

        // The coordinates of the left square background
        BackRectLeft = LEFT_CENTERX - BACK_RECT_SIZE;
        BackRectTop = LEFT_CENTERY - BACK_RECT_SIZE;
        BackRectRight = LEFT_CENTERX + BACK_RECT_SIZE;
        BackRectButtom = LEFT_CENTERY + BACK_RECT_SIZE;

        // The X and Y coordinates of the left joystick,
        // and the radius of the joystick
        SmallRockerCircleX = LEFT_CENTERX;
        SmallRockerCircleY = LEFT_CENTERY;
        SmallRockerCircleR = Height / 4;

        // The coordinates of the right square background
        BackRectLeft2 = RIGHT_CENTERX - BACK_RECT_SIZE;
        BackRectTop2 = RIGHT_CENTERY - BACK_RECT_SIZE;
        BackRectRight2 = RIGHT_CENTERX + BACK_RECT_SIZE;
        BackRectButtom2 = RIGHT_CENTERY + BACK_RECT_SIZE;

        // The X and Y coordinates of the right joystick
        // and the radius of the joystick
        SmallRockerCircleX2 = RIGHT_CENTERX;
        SmallRockerCircleY2 = RIGHT_CENTERY;
        SmallRockerCircleR2 = Height / 4;


        rightTouchStartX = RIGHT_CENTERX;
        rightTouchStartY = RIGHT_CENTERY;
        leftTouchStartX = LEFT_CENTERX;
        leftTouchStartY = LEFT_CENTERY;

        logoLocation = new RectF(screenWidth/2 - 150,BackRectButtom - 107,screenWidth/2 + 150,BackRectButtom);

    }
	/***
	 * Get the two-point line and the x-axis of the arc
	 */
	public double getRad(float px1, float py1, float px2, float py2) {
		//The distance between two points X is obtained
		float x = px2 - px1;
		//The distance between two points Y is obtained
		float y = py1 - py2;
		//Calculate the length of the hypotenuse
		float xie = (float) Math.sqrt(Math.pow(x, 2) + Math.pow(y, 2));
		//Get the cosine of this angle (by the theorem in the
        // trigonometric function: Adjacent edge / hypotenuse = angle cosine)
		float cosAngle = x / xie;
		//The arc of its angle is obtained by the arc cosine theorem
		float rad = (float) Math.acos(cosAngle);
		//Note: When the touch screen position Y coordinates less than
        // rocker Y coordinates we have to take the opposite value -0 ~ -180
		if (py2 < py1) {
			rad = -rad;
		}
		return rad;
	}

    //Using a simplified program to achieve dual-rocker control
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        try {
            int pointNum = event.getPointerCount();

            float x1, y1, x2, y2;    //Touch Positon
            float leftX = 0, leftY = 0, rightX = 0, rightY = 0;

            final float DIVIDE_X = (LEFT_CENTERX + RIGHT_CENTERX) / 2;

		    /*Release touch*/
            switch ((event.getAction() & MotionEvent.ACTION_MASK)) {
                case MotionEvent.ACTION_UP:    //the last release
                    Log.v(TAG, "ACTION_UP");
                    Log.v(TAG, "PointNum:" + Integer.toString(event.getPointerCount()) + ",actionIndex:"
                            + Integer.toString(event.getActionIndex()));
                    Log.v(TAG, "X:" + Float.toString(event.getX()) + ";Y:" + Float.toString(event.getY()));

                    leftTouching = false;
                    rightTouching = false;

                    //Let go home
                    SmallRockerCircleX = LEFT_CENTERX;
                    SmallRockerCircleY = LEFT_CENTERY;

                    if (altCtrlMode == 1)    //Fixed high climb
                        SmallRockerCircleY = LEFT_CENTERY;

                    SmallRockerCircleX2 = RIGHT_CENTERX;
                    SmallRockerCircleY2 = RIGHT_CENTERY;

                    leftTouchStartX = LEFT_CENTERX;
                    if (altCtrlMode == 1) {
                        leftTouchStartY = LEFT_CENTERY;
                    }

                    rightTouchStartX = RIGHT_CENTERX;
                    rightTouchStartY = RIGHT_CENTERY;

                    break;
                case MotionEvent.ACTION_POINTER_UP://first release if two finger is touching
                    if (event.getX(event.getActionIndex()) < DIVIDE_X) {
                        leftTouching = false;
                        SmallRockerCircleX = LEFT_CENTERX;
                        if (altCtrlMode == 1)    //Fixed high climb
                            SmallRockerCircleY = LEFT_CENTERY;

                        leftTouchStartX = LEFT_CENTERX;
                        leftTouchStartY = LEFT_CENTERY;

                        rightTouchIndex = 0;
                    } else {
                        rightTouching = false;
                        SmallRockerCircleX2 = RIGHT_CENTERX;
                        SmallRockerCircleY2 = RIGHT_CENTERY;
                        rightTouchStartX = RIGHT_CENTERX;
                        rightTouchStartY = RIGHT_CENTERY;
                    }

                    Log.v(TAG, "ACTION_POINTER_UP");
                    Log.v(TAG, "PointNum:" + Integer.toString(event.getPointerCount()) + ",actionIndex:" + Integer.toString(event.getActionIndex()));
                    break;
            }

		    /*get touch*/
            if (event.getAction() == MotionEvent.ACTION_DOWN
                    || event.getAction() == MotionEvent.ACTION_MOVE
                    || (event.getAction() & MotionEvent.ACTION_MASK) == MotionEvent.ACTION_POINTER_DOWN) {
                if (pointNum == 1) {
                    x1 = event.getX();
                    y1 = event.getY();
                    if (x1 < DIVIDE_X) {
                        leftX = x1;
                        leftY = y1;
                        if (leftTouching == false) {
                            leftTouchStartX = leftX;
                            leftTouchStartY = leftY;
                            leftTouching = true;
                        }

                    } else if (x1 >= DIVIDE_X) {
                        rightX = x1;
                        rightY = y1;
                        if (rightTouching == false) {
                            rightTouchStartX = rightX;
                            rightTouchStartY = rightY;
                            rightTouching = true;
                        }
                    }
                } else if (pointNum > 1) {
                    x1 = event.getX();
                    y1 = event.getY();
                    x2 = event.getX(1);
                    y2 = event.getY(1);
                    if (x1 < x2) {
                        if (x1 < DIVIDE_X) {
                            leftX = x1;
                            leftY = y1;
                            if (leftTouching == false) {
                                leftTouchStartX = leftX;
                                leftTouchStartY = leftY;
                                leftTouching = true;
                            }

                        }
                        if (x2 > DIVIDE_X) {
                            rightX = x2;
                            rightY = y2;
                            if (rightTouching == false) {
                                rightTouchStartX = rightX;
                                rightTouchStartY = rightY;
                                rightTouching = true;
                            }
                        }
                    } else {
                        if (x2 < DIVIDE_X) {
                            leftX = x2;
                            leftY = y2;
                            if (leftTouching == false) {
                                leftTouchStartX = leftX;
                                leftTouchStartY = leftY;
                                leftTouching = true;
                            }
                        }
                        if (x1 > DIVIDE_X) {
                            rightX = x1;
                            rightY = y1;
                            if (rightTouching == false) {
                                rightTouchStartX = rightX;
                                rightTouchStartY = rightY;
                                rightTouching = true;
                            }
                        }
                    }
                }

                /**Process movement**/
                if (leftTouching == true)//Left Stick is touched
                {
                    Log.v(TAG, "leftX: " + Float.toString(leftX) + "  leftY: " + Float.toString(leftY));
                    SmallRockerCircleX = leftX;
                    SmallRockerCircleY = leftY;

                    Log.v(TAG, Float.toString(SmallRockerCircleY) + " " + Float.toString(BackRectButtom));

                }
                //Right Stick is touched
                if (rightTouching == true) {
                    Log.v(TAG, "rightX: " + Float.toString(rightX) + "  rightY: " + Float.toString(rightY));

                    SmallRockerCircleX2 = rightX;
                    SmallRockerCircleY2 = rightY;

                    Log.v(TAG, Float.toString(SmallRockerCircleY2) + " " + Float.toString(BackRectButtom2));
                }
            }

            //coordinate of the center of left and right joystick (x,y)
            //Left and right joystick center point coordinates (x, y)
            Log.v(TAG, "left(x):"+Integer.toString((int) leftTouchStartX) + " left(y):"
                    + Integer.toString((int) leftTouchStartY) + " right(x):"
                    + Integer.toString((int) rightTouchStartX) + " right(y):"
                    + Integer.toString((int) rightTouchStartY));

            if (YAW_STOP_CONTROL == 1)
                SmallRockerCircleX = LEFT_CENTERX;    //Temporarily control yaw,
                                // to avoid the delay when the control throttle

            //Drop hands as a starting point, except throttle
            if (altCtrlMode == 0)
                Protocol.throttle = (int) (1000 + 1000 * (BackRectButtom - SmallRockerCircleY) / (BackRectButtom - BackRectTop));
            else
                Protocol.throttle = (int) (1500 - 1000 * (SmallRockerCircleY - leftTouchStartY) / (BackRectButtom - BackRectTop));
            Protocol.yaw = (int) (1500 + 1000 * ((SmallRockerCircleX - leftTouchStartX)) / (BackRectRight - BackRectLeft));
            Protocol.pitch = (int) (1500 + 1000 * (0 - (SmallRockerCircleY2 - rightTouchStartY)) / (BackRectButtom2 - BackRectTop2));
            Protocol.roll = (int) (1500 + 1000 * ((SmallRockerCircleX2 - rightTouchStartX)) / (BackRectRight2 - BackRectLeft2));

            Protocol.throttle = constrainRange(Protocol.throttle, 1000, 2000);
            Protocol.yaw = constrainRange(Protocol.yaw, 1000, 2000);
            Protocol.pitch = constrainRange(Protocol.pitch, 1000, 2000);
            Protocol.roll = constrainRange(Protocol.roll, 1000, 2000);

            Log.i(TAG, "yaw: " + Integer.toString(Protocol.yaw)
                    + " trottle: " + Integer.toString(Protocol.throttle)
                    + " pitch: " + Integer.toString(Protocol.pitch)
                    + " roll: " + Integer.toString(Protocol.roll));

            touchReadyToSend = true;


        } catch (Exception e) {//stick turn out error
            Log.e("stickError", "stickError");
            SmallRockerCircleX = LEFT_CENTERX;
            SmallRockerCircleY = LEFT_CENTERY;
            SmallRockerCircleX2 = RIGHT_CENTERX;
            SmallRockerCircleY2 = RIGHT_CENTERY;
            leftTouching = false;
            rightTouching = false;
            leftTouchIndex = 0;
            rightTouchIndex = 0;
        }
        return true;
    }

    public int constrainRange(int x,int min,int max)
	{
		if(x<min) x=min;
		if(x>max) x=max;
		
		return x;
		
	}
	
	public int rc2StickPosY(int rc)
	{
		int posY=0;
		posY=(int)(BackRectButtom-(BackRectButtom-BackRectTop)*(rc-1000)/1000.0);
		return posY;
	}
	/**
	 * 
	 * @param R
	 *            The rotational point of circular motion
	 * @param centerX
	 *            Rotate point X
	 * @param centerY
	 *            The rotation point Y
	 * @param rad
	 *            Rotation of the arc
	 */
	public void getXY(float centerX, float centerY, float R, double rad) {
		//Gets the X coordinate of the circular motion
		SmallRockerCircleX = (float) (R * Math.cos(rad)) + centerX;
		//Gets the Y coordinate of the circular motion
		SmallRockerCircleY = (float) (R * Math.sin(rad)) + centerY;
	}
	
	

	public void draw() {
		try {
			canvas = sfh.lockCanvas();
			canvas.drawColor(Color.BLACK);
			//Sets the color
			//Draw the rocker background
			paint.setColor(Color.WHITE);
			canvas.drawRect(BackRectLeft,BackRectTop,BackRectRight,BackRectButtom,paint);///
			//Draw the joystick
			paint.setColor(0x4F94CD00); 
			canvas.drawCircle(SmallRockerCircleX, SmallRockerCircleY, SmallRockerCircleR, paint);

			//Draw another Right one
			paint.setColor(Color.WHITE); 
			canvas.drawRect(BackRectLeft2,BackRectTop2,BackRectRight2,BackRectButtom2,paint);///
			paint.setColor(0x4F94CD00);
			canvas.drawCircle(SmallRockerCircleX2, SmallRockerCircleY2, SmallRockerCircleR2, paint);

            //Draw the background
//            Bitmap back = BitmapFactory.decodeResource(this.getResources(), R.drawable.logo);
//            canvas.drawBitmap(back, null,logoLocation, null);


        } catch (Exception e) {
			// TODO: handle exception
		} finally {
			try {
				if (canvas != null)
					sfh.unlockCanvasAndPost(canvas);
			} catch (Exception e2) {

			}
		}
	}

    //Thread run operation, when the surface is created, the thread open
	public void run() {
		// TODO Auto-generated method stub
		//
		while (flag) {	
			draw();
			try {
				Thread.sleep(50);	//The thread sleeps for 50ms
			} catch (Exception ex) {
			}
		}
	}

	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
		Log.v("Himi", "surfaceChanged");

        WindowManager wm = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
        int WMwidth = wm.getDefaultDisplay().getWidth();
        int WMheight = wm.getDefaultDisplay().getHeight();

        Log.v("viewSize","height:"+ WMheight + "  Width:"+WMwidth);

        stickSizeInit(WMwidth,WMheight/2);	//Obain the height of this view
	}

	public void surfaceDestroyed(SurfaceHolder holder) {
		flag = false;
		Log.v("Himi", "surfaceDestroyed");
	}
}