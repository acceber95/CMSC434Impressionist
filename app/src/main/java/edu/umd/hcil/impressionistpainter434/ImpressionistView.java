package edu.umd.hcil.impressionistpainter434;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by jon on 3/20/2016.
 */
public class ImpressionistView extends View {

    // ------------------------------ PRIVATE FIELDS ------------------------------

    // Image fields
    private ImageView _imageView;
    private Bitmap _imageBitmap;

    // Canvas set up
    private Canvas _offScreenCanvas = null;
    private Bitmap _offScreenBitmap = null;
    private Paint _paint = new Paint();
    private Paint _paintBorder = new Paint();

    // Paint details
    private int _alpha = 230;
    private float _minBrushRadius = 8;
    private BrushType _brushType = BrushType.Square;

    // Data structures to store past data
    private Map<Integer, Point> _lastPoints = new HashMap<Integer, Point>();
    private Map<Integer, Long> _lastPointTimes = new HashMap<Integer, Long>();


    // ------------------------------ INITIALIZATION ------------------------------

    public ImpressionistView(Context context) {
        super(context);
        init(null, 0);
    }

    public ImpressionistView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs, 0);
    }

    public ImpressionistView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(attrs, defStyle);
    }

    /**
     * Because we have more than one constructor (i.e., overloaded constructors), we use
     * a separate initialization method
     * @param attrs
     * @param defStyle
     */
    private void init(AttributeSet attrs, int defStyle) {

        // Set setDrawingCacheEnabled to true to support generating a bitmap copy of the view (for saving)
        // See: http://developer.android.com/reference/android/view/View.html#setDrawingCacheEnabled(boolean)
        //      http://developer.android.com/reference/android/view/View.html#getDrawingCache()
        this.setDrawingCacheEnabled(true);

        // Set up main Paint object
        _paint.setColor(Color.RED);
        _paint.setAlpha(_alpha);
        _paint.setAntiAlias(true);
        _paint.setStyle(Paint.Style.FILL);
        _paint.setStrokeWidth(4);

        // Set up paint object for border
        _paintBorder.setColor(Color.BLACK);
        _paintBorder.setStrokeWidth(3);
        _paintBorder.setStyle(Paint.Style.STROKE);
        _paintBorder.setAlpha(50);
    }


    // ------------------------------ METHODS ------------------------------

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        Bitmap bitmap = getDrawingCache();
        Log.v("onSizeChanged", MessageFormat.format("bitmap={0}, w={1}, h={2}, oldw={3}, oldh={4}", bitmap, w, h, oldw, oldh));
        if(bitmap != null) {
            _offScreenBitmap = getDrawingCache().copy(Bitmap.Config.ARGB_8888, true);
            _offScreenCanvas = new Canvas(_offScreenBitmap);
        }
    }

    /**
     * Returns the cached Bitmap
     * @return
     */
    protected Bitmap getBitmap() {
        return _offScreenBitmap;
    }

    /**
     * Sets the ImageView, which hosts the image that we will paint in this view
     * @param imageView
     */
    public void setImageView(ImageView imageView) {
        _imageView = imageView;
    }

    /**
     * Sets the brush type. Feel free to make your own and completely change my BrushType enum
     * @param brushType
     */
    public void setBrushType(BrushType brushType){
        _brushType = brushType;
    }

    /**
     * Clears the painting
     */
    public void clearPainting() {
        _offScreenCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        invalidate();
    }

    /**
     * Draws the canvas for user interaction
     * @param canvas
     */
    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Loads the cached bitmap
        if(_offScreenBitmap != null) {
            canvas.drawBitmap(_offScreenBitmap, 0, 0, _paint);
        }

        // Draw the border. Helpful to see the size of the bitmap in the ImageView
        canvas.drawRect(getBitmapPositionInsideImageView(_imageView), _paintBorder);
    }

    @Override
    public boolean onTouchEvent(MotionEvent motionEvent) {

        _imageBitmap = _imageView.getDrawingCache(); // Retrieve image bitmap

        // --- ITERATE THROUGH POINTERS (MULTI-TOUCH) ---
        for(int i = 0; i < motionEvent.getPointerCount(); i++) {

            float touchX = motionEvent.getX(i);
            float touchY = motionEvent.getY(i);

            int pointerId = motionEvent.getPointerId(i);
            int action = motionEvent.getActionMasked();
            int historySize = motionEvent.getHistorySize();

            // instantiate the previous point and time
            if(_lastPoints.get(pointerId) == null) {
                _lastPoints.put(pointerId, new Point());
                _lastPointTimes.put(pointerId, -1L);
            }

            if (_imageBitmap != null) {

                // --- Check for out of bounds exceptions ---
                if (touchX < 0) touchX = 0;
                if (touchX >= _imageBitmap.getWidth()) touchX = _imageBitmap.getWidth() - 1;
                if (touchY < 0) touchY = 0;
                if (touchY >= _imageBitmap.getHeight()) touchY = _imageBitmap.getHeight() - 1;

                // --- Set the pixel color ---
                int pixelColor = _imageBitmap.getPixel((int) touchX, (int) touchY); // TODO: fix dimensions
                _paint.setColor(pixelColor);
                _paint.setAlpha(_alpha);

                // --- Retrieve previous data point information ---
                float lastX = _lastPoints.get(pointerId).x;
                float lastY = _lastPoints.get(pointerId).y;
                float lastTime = _lastPointTimes.get(pointerId);

                // --- Calculate distance and speed ---
                double distance = Math.sqrt((touchX - lastX) * (touchX - lastX) + (touchY - lastY) * (touchY - lastY));
                double speed = distance / (System.currentTimeMillis() - lastTime + 20);
                double velocityDepRadius = _minBrushRadius * (speed + 0.2);

                // --- BRUSHES ---

                /* CIRCLE brush */
                if (_brushType == BrushType.Circle) {
                    switch (action) {
                        case MotionEvent.ACTION_DOWN:
                        case MotionEvent.ACTION_POINTER_DOWN:
                            velocityDepRadius = _minBrushRadius; // initial radius
                        case MotionEvent.ACTION_MOVE:
                            // Historical points
                            for (int h = 0; h < historySize; h++) {
                                float histX = motionEvent.getHistoricalX(h);
                                float histY = motionEvent.getHistoricalY(h);
                                _offScreenCanvas.drawCircle(histX, histY, (int) velocityDepRadius, _paint);
                            }
                            // Current point
                            _offScreenCanvas.drawCircle(touchX, touchY, (int) velocityDepRadius, _paint);
                            break;
                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_POINTER_UP:
                            removePointer(pointerId);
                            break;
                    }
                /* SQUARE brush */
                } else if (_brushType == BrushType.Square) {
                    switch (action) {
                        case MotionEvent.ACTION_DOWN:
                        case MotionEvent.ACTION_POINTER_DOWN:
                            velocityDepRadius = _minBrushRadius; // initial radius
                        case MotionEvent.ACTION_MOVE:
                            // Historical points
                            for (int h = 0; h < historySize; h++) {
                                float histX = motionEvent.getHistoricalX(h);
                                float histY = motionEvent.getHistoricalY(h);
                                Rect histRect = getSquareRect(histX, histY, velocityDepRadius);
                                _offScreenCanvas.drawRect(histRect, _paint);
                            }
                            // Current point
                            Rect currRect = getSquareRect(touchX, touchY, velocityDepRadius);
                            _offScreenCanvas.drawRect(currRect, _paint);
                            break;
                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_POINTER_UP:
                            removePointer(pointerId);
                            break;
                    }
                /* LINE brush */
                } else if (_brushType == BrushType.Line) {
                    switch (action) {
                        case MotionEvent.ACTION_DOWN:
                        case MotionEvent.ACTION_POINTER_DOWN:
                            speed = 1; // initial speed
                        case MotionEvent.ACTION_MOVE:
                            // Calculate the line start/end points via algebra
                            float m = (lastX - touchX) / (touchY - lastY);
                            float dist = (float) (1500 * (speed + 1));
                            float startX = (float) (touchX - Math.sqrt(dist / ((1 + m * m))));
                            float stopX = (float) (touchX + Math.sqrt(dist / ((1 + m * m))));
                            float startY = (float) (touchY + Math.sqrt(dist / (1 + 1 / m / m)));
                            float stopY = (float) (touchY - Math.sqrt(dist / (1 + 1 / m / m)));
                            // Draw on bitmap
                            _offScreenCanvas.drawLine(startX, startY, stopX, stopY, _paint);
                            break;
                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_POINTER_UP:
                            removePointer(pointerId);
                            break;
                    }
                }
            }
            // --- Set the previous point/time ---
            _lastPoints.put(pointerId, new Point((int) touchX, (int) touchY));
            _lastPointTimes.put(pointerId, System.currentTimeMillis());

            invalidate(); // Force call onDraw()
        }
        return true;
    }

    /**
     * Helper method to retrieve the Rect object for SQUARE brush touches
     * @param x
     * @param y
     * @param radius
     * @return
     */
    private Rect getSquareRect(float x, float y, double radius) {
        int left = (int) (x - radius);
        int top = (int) (y - radius);
        int right = (int) (x + radius);
        int bottom = (int) (y + radius);
        return new Rect(left, top, right, bottom);
    }

    /**
     * Helper method to remove the pointer from the appropriate data structures
     * @param pointerId
     */
    private void removePointer(int pointerId) {
        _lastPoints.remove(pointerId);
        _lastPointTimes.remove(pointerId);
    }


    /**
     * This method is useful to determine the bitmap position within the Image View. It's not needed for anything else
     * Modified from:
     *  - http://stackoverflow.com/a/15538856
     *  - http://stackoverflow.com/a/26930938
     * @param imageView
     * @return
     */
    private static Rect getBitmapPositionInsideImageView(ImageView imageView) {
        Rect rect = new Rect();

        if (imageView == null || imageView.getDrawable() == null) {
            return rect;
        }

        // Get image dimensions
        // Get image matrix values and place them in an array
        float[] f = new float[9];
        imageView.getImageMatrix().getValues(f);

        // Extract the scale values using the constants (if aspect ratio maintained, scaleX == scaleY)
        final float scaleX = f[Matrix.MSCALE_X];
        final float scaleY = f[Matrix.MSCALE_Y];

        // Get the drawable (could also get the bitmap behind the drawable and getWidth/getHeight)
        final Drawable d = imageView.getDrawable();
        final int origW = d.getIntrinsicWidth();
        final int origH = d.getIntrinsicHeight();

        // Calculate the actual dimensions
        final int widthActual = Math.round(origW * scaleX);
        final int heightActual = Math.round(origH * scaleY);

        // Get image position
        // We assume that the image is centered into ImageView
        int imgViewW = imageView.getWidth();
        int imgViewH = imageView.getHeight();

        int top = (int) (imgViewH - heightActual)/2;
        int left = (int) (imgViewW - widthActual) / 2;

        rect.set(left, top, left + widthActual, top + heightActual);

        return rect;
    }
}

