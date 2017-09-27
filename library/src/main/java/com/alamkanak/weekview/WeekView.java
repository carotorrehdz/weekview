package com.alamkanak.weekview;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GestureDetectorCompat;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.animation.FastOutLinearInInterpolator;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.OverScroller;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static com.alamkanak.weekview.WeekViewUtil.isSameDay;
import static com.alamkanak.weekview.WeekViewUtil.today;

/**
 * Created by Raquib-ul-Alam Kanak on 7/21/2014.
 * Website: http://alamkanak.github.io/
 */
public class WeekView extends View {

    private static final int HOURS = 23;
    private static final String TIME_TEXT = "00 PM";

    private enum Direction {
        NONE, LEFT, RIGHT, VERTICAL
    }

    private final Context mContext;

    private boolean mAreDimensionsInvalid = true;
    private boolean mIsFirstDraw = true;
    private boolean mIsZooming;
    private boolean mRefreshEvents = false;
    private Calendar mScrollToDay = null;
    private Direction mCurrentFlingDirection = Direction.NONE;
    private Direction mCurrentScrollDirection = Direction.NONE;
    private double mScrollToHour = -1;
    private float mXScrollingSpeed = 1f;
    private GestureDetectorCompat mGestureDetector;
    private int mFetchedPeriod = -1;
    private int mMinimumFlingVelocity = 0;
    private int mScaledTouchSlop = 0;
    private List<EventRect> mEventRects;
    private List<? extends WeekViewEvent> mCurrentPeriodEvents;
    private List<? extends WeekViewEvent> mNextPeriodEvents;
    private List<? extends WeekViewEvent> mPreviousPeriodEvents;
    private OverScroller mScroller;
    private PointF mCurrentOrigin = new PointF(0f, 0f);
    private ScaleGestureDetector mScaleDetector;

    // Text.
    private int mBlackTextColor = Color.rgb(81, 91, 94);
    private int mGrayTextColor = Color.rgb(160, 168, 170);
    private int mRedTextColor = Color.rgb(255, 67, 55);
    private int mTextSize = 0;

    // Background.
    private Paint mBackgroundPaint;

    // Grid.
    private float mGridRadio = 0;
    private int mGridColor = Color.rgb(232, 235, 237);
    private int mGridThickness = 0;

    // Days.
    private float mWidthPerDay;
    private int mDayHeight = 0;
    private int mFirstDayOfWeek = Calendar.MONDAY;
    private int mNumberOfVisibleDays = 5;
    private float mHeaderHeight;
    private Paint mHeaderTextPaint;

    // All Day.
    private int mAllDayEventHeight = 0;
    private Paint mAllDayBackgroundPaint;
    private Paint mAllDayTextPaint;
    private String mAllDayText;

    // Time.
    private float mTimeColumnWidth;
    private float mTimeTextHeight;
    private int mEffectiveMinHourHeight = 0;
    private int mHourHeight = 0;
    private int mMaxHourHeight = 0;
    private int mMinHourHeight = 0;
    private int mNewHourHeight = -1;
    private int mTimeColumnPadding = 0;
    private Paint mGridPaint;
    private Paint mHourPaint;
    private Paint mPeriodPaint;

    // Now.
    private int mNowLineColor = Color.rgb(81, 91, 94);
    private int mNowLineThickness = 0;
    private Paint mNowLinePaint;

    // Events.
    private int mEventCornerRadius = 0;
    private int mEventDrawableSize = 0;
    private int mEventMargin = 0;
    private int mEventPadding = 0;
    private int mEventTextSize = 0;
    private int mOverlappingEventGap = 0;
    private Paint mEventBackgroundPaint;
    private TextPaint mEventTextPaint;

    // Listeners.
    private DateTimeInterpreter mDateTimeInterpreter;
    private EmptyViewClickListener mEmptyViewClickListener;
    private EmptyViewLongPressListener mEmptyViewLongPressListener;
    private EventClickListener mEventClickListener;
    private EventLongPressListener mEventLongPressListener;
    private WeekViewLoader mWeekViewLoader;

    private final GestureDetector.SimpleOnGestureListener mGestureListener = new GestureDetector.SimpleOnGestureListener() {

        @Override
        public boolean onDown(MotionEvent e) {
            goToNearestOrigin();

            return true;
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            // Check if view is zoomed.
            if (mIsZooming) {
                return true;
            }

            switch (mCurrentScrollDirection) {
                case NONE: {
                    // Allow scrolling only in one direction.
                    if (Math.abs(distanceX) > Math.abs(distanceY)) {
                        if (distanceX > 0) {
                            mCurrentScrollDirection = Direction.LEFT;
                        } else {
                            mCurrentScrollDirection = Direction.RIGHT;
                        }
                    } else {
                        mCurrentScrollDirection = Direction.VERTICAL;
                    }
                    break;
                }
                case LEFT: {
                    // Change direction if there was enough change.
                    if (Math.abs(distanceX) > Math.abs(distanceY) && (distanceX < -mScaledTouchSlop)) {
                        mCurrentScrollDirection = Direction.RIGHT;
                    }
                    break;
                }
                case RIGHT: {
                    // Change direction if there was enough change.
                    if (Math.abs(distanceX) > Math.abs(distanceY) && (distanceX > mScaledTouchSlop)) {
                        mCurrentScrollDirection = Direction.LEFT;
                    }
                    break;
                }
            }

            // Calculate the new origin after scroll.
            switch (mCurrentScrollDirection) {
                case LEFT:
                case RIGHT:
                    mCurrentOrigin.x -= distanceX * mXScrollingSpeed;
                    ViewCompat.postInvalidateOnAnimation(WeekView.this);
                    break;
                case VERTICAL:
                    mCurrentOrigin.y -= distanceY;
                    ViewCompat.postInvalidateOnAnimation(WeekView.this);
                    break;
            }

            return true;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            if (mIsZooming) {
                return true;
            }

            mScroller.forceFinished(true);
            mCurrentFlingDirection = mCurrentScrollDirection;

            switch (mCurrentFlingDirection) {
                case LEFT:
                case RIGHT:
                    mScroller.fling((int) mCurrentOrigin.x, (int) mCurrentOrigin.y, (int) (velocityX * mXScrollingSpeed), 0, Integer.MIN_VALUE, Integer.MAX_VALUE, (int) -(mHourHeight * HOURS + mHeaderHeight + mHourHeight + mTimeTextHeight / 2 - getHeight()), 0);
                    break;
                case VERTICAL:
                    mScroller.fling((int) mCurrentOrigin.x, (int) mCurrentOrigin.y, 0, (int) velocityY, Integer.MIN_VALUE, Integer.MAX_VALUE, (int) -(mHourHeight * HOURS + mHeaderHeight + mHourHeight + mTimeTextHeight / 2 - getHeight()), 0);
                    break;
            }

            ViewCompat.postInvalidateOnAnimation(WeekView.this);

            return true;
        }


        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            // If the tap was on an event then trigger the callback.
            if (mEventRects != null && mEventClickListener != null) {
                List<EventRect> reversedEventRects = mEventRects;
                Collections.reverse(reversedEventRects);

                for (EventRect event : reversedEventRects) {
                    if (event.rectF != null && e.getX() > event.rectF.left && e.getX() < event.rectF.right && e.getY() > event.rectF.top && e.getY() < event.rectF.bottom) {
                        mEventClickListener.onEventClick(event.originalEvent, event.rectF);
                        playSoundEffect(SoundEffectConstants.CLICK);

                        return super.onSingleTapConfirmed(e);
                    }
                }
            }

            // If the tap was on in an empty space, then trigger the callback.
            if (mEmptyViewClickListener != null && e.getX() > mTimeColumnWidth && e.getY() > (mHeaderHeight)) {
                Calendar selectedTime = getTimeFromPoint(e.getX(), e.getY());

                if (selectedTime != null) {
                    playSoundEffect(SoundEffectConstants.CLICK);
                    mEmptyViewClickListener.onEmptyViewClicked(selectedTime);
                }
            }

            return super.onSingleTapConfirmed(e);
        }

        @Override
        public void onLongPress(MotionEvent e) {
            super.onLongPress(e);

            if (mEventLongPressListener != null && mEventRects != null) {
                List<EventRect> reversedEventRects = mEventRects;
                Collections.reverse(reversedEventRects);

                for (EventRect event : reversedEventRects) {
                    if (event.rectF != null && e.getX() > event.rectF.left && e.getX() < event.rectF.right && e.getY() > event.rectF.top && e.getY() < event.rectF.bottom) {
                        mEventLongPressListener.onEventLongPress(event.originalEvent, event.rectF);
                        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);

                        return;
                    }
                }
            }

            // If the tap was on in an empty space, then trigger the callback.
            if (mEmptyViewLongPressListener != null && e.getX() > mTimeColumnWidth && e.getY() > (mHeaderHeight)) {
                Calendar selectedTime = getTimeFromPoint(e.getX(), e.getY());

                if (selectedTime != null) {
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    mEmptyViewLongPressListener.onEmptyViewLongPress(selectedTime);
                }
            }
        }
    };

    // region Constructor
    public WeekView(Context context) {
        this(context, null);
    }

    public WeekView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public WeekView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        mContext = context;

        // Get attribute values.
        TypedArray a = context.getTheme().obtainStyledAttributes(attrs, R.styleable.WeekView, 0, 0);

        try {
            mTextSize = a.getDimensionPixelSize(R.styleable.WeekView_textSize, (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, mTextSize, context.getResources().getDisplayMetrics()));
            mGridThickness = Math.round(a.getDimensionPixelSize(R.styleable.WeekView_gridThickness, mGridThickness) / 2) * 2;
            mGridRadio = mGridThickness / 2;
            mDayHeight = a.getDimensionPixelSize(R.styleable.WeekView_dayHeight, mDayHeight);
            mFirstDayOfWeek = a.getInteger(R.styleable.WeekView_firstDayOfWeek, mFirstDayOfWeek);
            mNumberOfVisibleDays = a.getInteger(R.styleable.WeekView_noOfVisibleDays, mNumberOfVisibleDays);
            mAllDayEventHeight = a.getDimensionPixelSize(R.styleable.WeekView_allDayEventHeight, mAllDayEventHeight);
            mAllDayText = a.getString(R.styleable.WeekView_allDayText);
            mHourHeight = a.getDimensionPixelSize(R.styleable.WeekView_hourHeight, mHourHeight);
            mMaxHourHeight = a.getDimensionPixelSize(R.styleable.WeekView_maxHourHeight, mMaxHourHeight);
            mMinHourHeight = a.getDimensionPixelSize(R.styleable.WeekView_minHourHeight, mMinHourHeight);
            mEffectiveMinHourHeight = mMinHourHeight;
            mTimeColumnPadding = a.getDimensionPixelSize(R.styleable.WeekView_timeColumnPadding, mTimeColumnPadding);
            mNowLineThickness = a.getDimensionPixelSize(R.styleable.WeekView_nowLineThickness, mNowLineThickness);
            mEventCornerRadius = a.getDimensionPixelSize(R.styleable.WeekView_eventCornerRadius, mEventCornerRadius);
            mEventDrawableSize = a.getDimensionPixelSize(R.styleable.WeekView_eventDrawableSize, mEventDrawableSize);
            mEventMargin = a.getDimensionPixelSize(R.styleable.WeekView_eventMargin, mEventMargin);
            mEventPadding = a.getDimensionPixelSize(R.styleable.WeekView_eventPadding, mEventPadding);
            mEventTextSize = a.getDimensionPixelSize(R.styleable.WeekView_eventTextSize, (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, mEventTextSize, context.getResources().getDisplayMetrics()));
            mOverlappingEventGap = a.getDimensionPixelSize(R.styleable.WeekView_overlappingEventGap, mOverlappingEventGap);
        } finally {
            a.recycle();
        }

        init();
    }

    private void init() {
        // Background.
        mBackgroundPaint = new Paint();
        mBackgroundPaint.setColor(Color.WHITE);

        // Grid.
        mGridPaint = new Paint();
        mGridPaint.setStyle(Paint.Style.STROKE);
        mGridPaint.setStrokeWidth(mGridThickness);
        mGridPaint.setColor(mGridColor);

        // Days.
        mHeaderTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mHeaderTextPaint.setColor(mBlackTextColor);
        mHeaderTextPaint.setTextAlign(Paint.Align.CENTER);
        mHeaderTextPaint.setTextSize(mTextSize);

        // All Day.
        mAllDayTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mAllDayTextPaint.setColor(mGrayTextColor);
        mAllDayTextPaint.setTextAlign(Paint.Align.CENTER);
        mAllDayTextPaint.setTextSize(mTextSize);

        mAllDayBackgroundPaint = new Paint();
        mAllDayBackgroundPaint.setColor(Color.rgb(247, 248, 248));

        // Time.
        mHourPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mHourPaint.setColor(mBlackTextColor);
        mHourPaint.setTextAlign(Paint.Align.LEFT);
        mHourPaint.setTextSize(mTextSize);
        Rect rect = new Rect();
        mHourPaint.getTextBounds(TIME_TEXT, 0, TIME_TEXT.length(), rect);

        mPeriodPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPeriodPaint.setColor(mGrayTextColor);
        mPeriodPaint.setTextAlign(Paint.Align.LEFT);
        mPeriodPaint.setTextSize(mTextSize);

        mTimeColumnWidth = mAllDayTextPaint.measureText(mAllDayText) + mTimeColumnPadding * 2;
        mTimeTextHeight = rect.height();

        // Now.
        mNowLinePaint = new Paint();
        mNowLinePaint.setStrokeWidth(mNowLineThickness);
        mNowLinePaint.setColor(mNowLineColor);

        // Events.
        mEventBackgroundPaint = new Paint();
        mEventBackgroundPaint.setColor(Color.WHITE);

        mEventTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.LINEAR_TEXT_FLAG);
        mEventTextPaint.setStyle(Paint.Style.FILL);
        mEventTextPaint.setColor(Color.WHITE);
        mEventTextPaint.setTextAlign(Paint.Align.LEFT);
        mEventTextPaint.setTextSize(mEventTextSize);

        // Scrolling.
        mGestureDetector = new GestureDetectorCompat(mContext, mGestureListener);
        mScroller = new OverScroller(mContext, new FastOutLinearInInterpolator());

        // Scale.
        mMinimumFlingVelocity = ViewConfiguration.get(mContext).getScaledMinimumFlingVelocity();
        mScaledTouchSlop = ViewConfiguration.get(mContext).getScaledTouchSlop();
        mScaleDetector = new ScaleGestureDetector(mContext, new ScaleGestureDetector.OnScaleGestureListener() {
            @Override
            public void onScaleEnd(ScaleGestureDetector detector) {
                mIsZooming = false;
            }

            @Override
            public boolean onScaleBegin(ScaleGestureDetector detector) {
                mIsZooming = true;
                goToNearestOrigin();

                return true;
            }

            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                mNewHourHeight = Math.round(mHourHeight * detector.getScaleFactor());
                invalidate();

                return true;
            }
        });
    }
    // endregion

    // region Draw methods
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Draw the header row.
        drawHeaderRowAndEvents(canvas);

        // Draw the time column and all the axes/separators.
        drawTimeColumnAndAxes(canvas);
    }

    private void drawTimeColumnAndAxes(Canvas canvas) {
        // Clip to paint in left column only.
        canvas.clipRect(0, mHeaderHeight, mTimeColumnWidth, getHeight(), Region.Op.REPLACE);

        for (int i = 0; i < HOURS; i++) {
            float top = mHeaderHeight + mHourHeight + mCurrentOrigin.y + mHourHeight * i;

            // Draw the text if its y position is not outside of the visible area. The pivot point of the text is the point at the bottom-right corner.
            String hour = getDateTimeInterpreter().interpretTime(i + 1);
            String period = getDateTimeInterpreter().interpretPeriod(i + 1);

            if (hour == null || period == null) {
                throw new IllegalStateException("A DateTimeInterpreter must not return null time");
            }

            if (top < getHeight()) {
                canvas.drawText(hour, mTimeColumnPadding, top + mTimeTextHeight / 2, mHourPaint);
                canvas.drawText(period, mTimeColumnPadding, top + mTimeTextHeight / 2 + mTimeColumnPadding * 2, mPeriodPaint);
            }
        }

        // Draw right line.
        canvas.drawLine(mTimeColumnWidth - mGridRadio, 0, mTimeColumnWidth - mGridRadio, getHeight(), mGridPaint);
    }

    private void drawHeaderRowAndEvents(Canvas canvas) {
        // Calculate the available width for each day.
        mWidthPerDay = (getWidth() - mTimeColumnWidth) / mNumberOfVisibleDays;

        // Calculate header height.
        mHeaderHeight = mDayHeight + mAllDayEventHeight;

        Calendar today = today();

        if (mAreDimensionsInvalid) {
            mEffectiveMinHourHeight = Math.max(mMinHourHeight, (int) ((getHeight() - mHeaderHeight - mHourHeight) / HOURS));
            mAreDimensionsInvalid = false;

            if (mScrollToDay != null) {
                goToDate(mScrollToDay);
            }

            mAreDimensionsInvalid = false;

            if (mScrollToHour >= 0) {
                goToHour(mScrollToHour);
            }

            mScrollToDay = null;
            mScrollToHour = -1;
            mAreDimensionsInvalid = false;
        }

        if (mIsFirstDraw) {
            mIsFirstDraw = false;

            // If the week view is being drawn for the first time, then consider the first day of the week.
            if (today.get(Calendar.DAY_OF_WEEK) != mFirstDayOfWeek) {
                mCurrentOrigin.x += mWidthPerDay * (today.get(Calendar.DAY_OF_WEEK) - mFirstDayOfWeek);
            }
        }

        // Calculate the new height due to the zooming.
        if (mNewHourHeight > 0) {
            if (mNewHourHeight < mEffectiveMinHourHeight) {
                mNewHourHeight = mEffectiveMinHourHeight;
            } else if (mNewHourHeight > mMaxHourHeight) {
                mNewHourHeight = mMaxHourHeight;
            }

            mCurrentOrigin.y = (mCurrentOrigin.y / mHourHeight) * mNewHourHeight;
            mHourHeight = mNewHourHeight;
            mNewHourHeight = -1;
        }

        // If the new mCurrentOrigin.y is invalid, make it valid.
        if (mCurrentOrigin.y < getHeight() - mHourHeight * HOURS - mHeaderHeight - mHourHeight - mTimeTextHeight / 2) {
            mCurrentOrigin.y = getHeight() - mHourHeight * HOURS - mHeaderHeight - mHourHeight - mTimeTextHeight / 2;
        }

        // Don't put an "else if" because it will trigger a glitch when completely zoomed out and scrolling vertically.
        if (mCurrentOrigin.y > 0) {
            mCurrentOrigin.y = 0;
        }

        // Consider scroll offset.
        int leftDaysWithGaps = (int) -(Math.ceil(mCurrentOrigin.x / mWidthPerDay));
        float startFromPixel = mCurrentOrigin.x + mWidthPerDay * leftDaysWithGaps + mTimeColumnWidth;
        float startPixel = startFromPixel;

        // Prepare to iterate for each day.
        Calendar day = (Calendar) today.clone();
        day.add(Calendar.HOUR, 6);

        // Prepare to iterate for each hour to draw the hour lines.
        int lineCount = (int) ((getHeight() - mHeaderHeight - mHourHeight) / mHourHeight) + 1;
        lineCount = (lineCount) * (mNumberOfVisibleDays + 1);
        float[] hourLines = new float[lineCount * 4];

        // Clear the cache for event rectangles.
        if (mEventRects != null) {
            for (EventRect eventRect : mEventRects) {
                eventRect.rectF = null;
            }
        }

        // Clip to paint events only.
        canvas.clipRect(mTimeColumnWidth, mHeaderHeight, getWidth(), getHeight(), Region.Op.REPLACE);
        canvas.drawRect(mTimeColumnWidth, mHeaderHeight, getWidth(), getHeight(), mBackgroundPaint);

        // Iterate through each day.
        Calendar mFirstVisibleDay = (Calendar) today.clone();
        mFirstVisibleDay.add(Calendar.DATE, -(Math.round(mCurrentOrigin.x / mWidthPerDay)));

        Calendar lastVisibleDay;

        for (int dayNumber = leftDaysWithGaps + 1; dayNumber <= leftDaysWithGaps + mNumberOfVisibleDays + 1; dayNumber++) {
            // Check if the day is today.
            day = (Calendar) today.clone();
            lastVisibleDay = (Calendar) day.clone();
            day.add(Calendar.DATE, dayNumber - 1);
            lastVisibleDay.add(Calendar.DATE, dayNumber - 2);
            boolean sameDay = isSameDay(day, today);

            // Get more events if necessary. We want to store the events 3 months beforehand. GeT events only when it is the first iteration of the loop.
            if (mEventRects == null || mRefreshEvents || (dayNumber == leftDaysWithGaps + 1 && mFetchedPeriod != (int) mWeekViewLoader.toWeekViewPeriodIndex(day) && Math.abs(mFetchedPeriod - mWeekViewLoader.toWeekViewPeriodIndex(day)) > 0.5)) {
                getMoreEvents(day);
                mRefreshEvents = false;
            }

            // Draw background color for each day.
            float start = (startPixel < mTimeColumnWidth ? mTimeColumnWidth : startPixel);

            if (mWidthPerDay + startPixel - start > 0) {
                canvas.drawLine(startPixel + mWidthPerDay - mGridRadio, 0, startPixel + mWidthPerDay - mGridRadio, getHeight(), mGridPaint);
            }

            // Prepare the separator lines for hours.
            int i = 0;

            for (int hourNumber = 0; hourNumber <= HOURS; hourNumber++) {
                float top = mHeaderHeight + mCurrentOrigin.y + mHourHeight * hourNumber;

                if (top > mHeaderHeight + mTimeTextHeight / 2 && top < getHeight() && startPixel + mWidthPerDay - start > 0) {
                    hourLines[i * 4] = start;
                    hourLines[i * 4 + 1] = top;
                    hourLines[i * 4 + 2] = startPixel + mWidthPerDay;
                    hourLines[i * 4 + 3] = top;
                    i++;
                }
            }

            // Draw the lines for hours.
            canvas.drawLines(hourLines, mGridPaint);

            // Draw the events.
            drawEvents(day, startPixel, canvas);

            // Draw the line at the current time.
            if (sameDay) {
                float startY = mHeaderHeight + mTimeTextHeight / 2 + mCurrentOrigin.y;
                Calendar now = Calendar.getInstance();
                float beforeNow = (now.get(Calendar.HOUR_OF_DAY) + now.get(Calendar.MINUTE) / 60.0f) * mHourHeight;
                canvas.drawLine(start, startY + beforeNow, startPixel + mWidthPerDay, startY + beforeNow, mNowLinePaint);
            }

            // In the next iteration, start from the next day.
            startPixel += mWidthPerDay;
        }

        // Draw 'All day' text.
        canvas.clipRect(0, 0, mTimeColumnWidth, mHeaderHeight, Region.Op.REPLACE);
        canvas.drawRect(0, mDayHeight, mTimeColumnWidth, mHeaderHeight, mAllDayBackgroundPaint);
        canvas.drawText(mAllDayText, mTimeColumnWidth / 2, mDayHeight - mGridThickness + mAllDayEventHeight / 2 + mAllDayTextPaint.getTextSize() / 2, mAllDayTextPaint);

        // 'All day' text right line.
        canvas.drawLine(mTimeColumnWidth - mGridRadio, 0, mTimeColumnWidth - mGridRadio, mHeaderHeight, mGridPaint);

        // 'All day' text top line.
        canvas.drawLine(0, mDayHeight + mGridRadio, mTimeColumnWidth, mDayHeight + mGridRadio, mGridPaint);

        // 'All day' text bottom line.
        canvas.drawLine(0, mHeaderHeight - mGridRadio, mTimeColumnWidth, mHeaderHeight - mGridRadio, mGridPaint);

        // Clip to paint header row only.
        canvas.clipRect(mTimeColumnWidth, 0, getWidth(), mHeaderHeight, Region.Op.REPLACE);

        // Draw 'All day' background.
        canvas.drawRect(0, mDayHeight, getWidth(), getHeight(), mAllDayBackgroundPaint);

        // 'All day' background top line.
        canvas.drawLine(0, mDayHeight + mGridRadio, getWidth(), mDayHeight + mGridRadio, mGridPaint);

        // 'All day' background bottom line.
        canvas.drawLine(0, mHeaderHeight - mGridRadio, getWidth(), mHeaderHeight - mGridRadio, mGridPaint);

        // Draw the header row texts.
        startPixel = startFromPixel;

        for (int dayNumber = leftDaysWithGaps + 1; dayNumber <= leftDaysWithGaps + mNumberOfVisibleDays + 1; dayNumber++) {
            // Check if the day is today.
            day = (Calendar) today.clone();
            day.add(Calendar.DATE, dayNumber - 1);

            // Draw the day labels.
            String dayLabel = getDateTimeInterpreter().interpretDate(day);

            if (dayLabel == null) {
                throw new IllegalStateException("A DateTimeInterpreter must not return null date");
            }

            // Draw day text.
            canvas.drawText(dayLabel, startPixel + mWidthPerDay / 2, mDayHeight / 2 + mHeaderTextPaint.getTextSize() / 2, mHeaderTextPaint);

            // Day right line.
            canvas.drawLine(startPixel + mWidthPerDay - mGridRadio, 0, startPixel + mWidthPerDay - mGridRadio, mHeaderHeight, mGridPaint);

            // Draw 'All day' events.
            drawAllDayEvents(day, startPixel, canvas);

            startPixel += mWidthPerDay;
        }
    }

    /**
     * Draw all the events of a particular day.
     *
     * @param date           The day.
     * @param startFromPixel The left position of the day area. The events will never go any left from this value.
     * @param canvas         The canvas to draw upon.
     */
    private void drawEvents(Calendar date, float startFromPixel, Canvas canvas) {
        if (mEventRects != null && mEventRects.size() > 0) {
            for (int i = 0; i < mEventRects.size(); i++) {
                if (isSameDay(mEventRects.get(i).event.getStartTime(), date) && !mEventRects.get(i).event.isAllDay()) {
                    // Calculate top.
                    float top = mHourHeight * 24 * mEventRects.get(i).top / 1440 + mCurrentOrigin.y + mHeaderHeight + mEventMargin;

                    // Calculate bottom.
                    float bottom = mHourHeight * 24 * mEventRects.get(i).bottom / 1440 + mCurrentOrigin.y + mHeaderHeight;

                    // Calculate left and right.
                    float left = startFromPixel + mEventRects.get(i).left * mWidthPerDay;

                    if (left < startFromPixel) {
                        left += mOverlappingEventGap;
                    } else {
                        left += mEventMargin;
                    }

                    float right = left + mEventRects.get(i).width * mWidthPerDay - mGridThickness;

                    if (right < startFromPixel + mWidthPerDay) {
                        right -= mOverlappingEventGap;
                    } else {
                        right -= mEventMargin * 2;
                    }

                    // Draw the event and the event name on top of it.
                    if (left < right && left < getWidth() && top < getHeight() && right > mTimeColumnWidth && bottom > mHeaderHeight) {
                        mEventRects.get(i).rectF = new RectF(left, top, right, bottom);
                        mEventBackgroundPaint.setColor(mEventRects.get(i).event.getColor() == 0 ? Color.WHITE : mEventRects.get(i).event.getColor());
                        canvas.drawRoundRect(mEventRects.get(i).rectF, mEventCornerRadius, mEventCornerRadius, mEventBackgroundPaint);
                        drawEventTitle(mEventRects.get(i).event, mEventRects.get(i).rectF, canvas, top, left);
                    } else {
                        mEventRects.get(i).rectF = null;
                    }
                }
            }
        }
    }

    /**
     * Draw all the All day-events of a particular day.
     *
     * @param date           The day.
     * @param startFromPixel The left position of the day area. The events will never go any left from this value.
     * @param canvas         The canvas to draw upon.
     */
    private void drawAllDayEvents(Calendar date, float startFromPixel, Canvas canvas) {
        if (mEventRects != null && mEventRects.size() > 0) {
            for (int i = 0; i < mEventRects.size(); i++) {
                if (isSameDay(mEventRects.get(i).event.getStartTime(), date) && mEventRects.get(i).event.isAllDay()) {
                    // Calculate top.
                    float top = mDayHeight + mGridThickness + mEventMargin;

                    // Calculate bottom.
                    float bottom = top + mEventRects.get(i).bottom;

                    // Calculate left and right.
                    float left = startFromPixel + mEventRects.get(i).left * mWidthPerDay;

                    if (left < startFromPixel) {
                        left += mOverlappingEventGap;
                    } else {
                        left += mEventMargin;
                    }

                    float right = left + mEventRects.get(i).width * mWidthPerDay - mGridThickness;

                    if (right < startFromPixel + mWidthPerDay) {
                        right -= mOverlappingEventGap;
                    } else {
                        right -= mEventMargin * 2;
                    }

                    // Draw the event and the event name on top of it.
                    if (left < right && left < getWidth() && top < getHeight() && right > mTimeColumnWidth && bottom > 0) {
                        mEventRects.get(i).rectF = new RectF(left, top, right, bottom);
                        mEventBackgroundPaint.setColor(mEventRects.get(i).event.getColor() == 0 ? Color.WHITE : mEventRects.get(i).event.getColor());
                        canvas.drawRoundRect(mEventRects.get(i).rectF, mEventCornerRadius, mEventCornerRadius, mEventBackgroundPaint);
                        drawEventTitle(mEventRects.get(i).event, mEventRects.get(i).rectF, canvas, top, left);
                    } else {
                        mEventRects.get(i).rectF = null;
                    }
                }
            }
        }
    }

    /**
     * Draw the name of the event on top of the event rectangle.
     *
     * @param event        The event of which the title (and location) should be drawn.
     * @param rect         The rectangle on which the text is to be drawn.
     * @param canvas       The canvas to draw upon.
     * @param originalTop  The original top position of the rectangle. The rectangle may have some of its portion outside of the visible area.
     * @param originalLeft The original left position of the rectangle. The rectangle may have some of its portion outside of the visible area.
     */
    private void drawEventTitle(WeekViewEvent event, RectF rect, Canvas canvas, float originalTop, float originalLeft) {
        if (rect.right - rect.left - mEventPadding * 2 < 0) {
            return;
        }

        if (rect.bottom - rect.top - mEventPadding * 2 < 0) {
            return;
        }

        // Prepare the name of the event.
        SpannableStringBuilder bob = new SpannableStringBuilder();

        if (event.getName() != null) {
            bob.append(event.getName());
            bob.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), 0, bob.length(), 0);
            bob.append(' ');
        }

        // Prepare the location of the event.
        if (event.getLocation() != null) {
            bob.append(event.getLocation());
        }

        int availableHeight = (int) (rect.bottom - originalTop - mEventPadding * 2);
        int availableWidth = (int) (rect.right - originalLeft - mEventPadding * 2);

        // Get text dimensions.
        StaticLayout textLayout = new StaticLayout(bob, mEventTextPaint, availableWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
        int lineHeight = textLayout.getHeight() / textLayout.getLineCount();

        if (availableHeight >= lineHeight) {
            // Calculate top.
            float top = originalTop + mEventPadding + (availableHeight - lineHeight) / 2.0f;

            // Calculate left.
            float left = originalLeft + mEventPadding;

            // Add extra space for drawable.
            if (event.hasDrawable()) {
                for (int i = 0; i <= 5; i++) {
                    bob.insert(0, " ");
                }
            }

            // Get text.
            textLayout = getTruncatedEventTitle(bob, availableHeight, availableWidth, lineHeight, event.isAllDay());

            if (textLayout.getLineCount() > 1) {
                top = originalTop + mEventPadding + (availableHeight - textLayout.getHeight()) / 2.0f;
            }

            // Draw drawable.
            if (event.hasDrawable()) {
                Drawable drawable = ContextCompat.getDrawable(getContext(), event.getDrawableId());
                drawable.setBounds((int) left, (int) top, (int) left + mEventDrawableSize, (int) (top + mEventDrawableSize));
                drawable.draw(canvas);
            }

            canvas.save();
            canvas.translate(left, top);
            textLayout.draw(canvas);
            canvas.restore();
        }
    }

    private StaticLayout getTruncatedEventTitle(SpannableStringBuilder bob, int availableHeight, int availableWidth, int lineHeight, boolean isAllDay) {
        if (isAllDay) {
            return new StaticLayout(TextUtils.ellipsize(bob, mEventTextPaint, availableWidth, TextUtils.TruncateAt.END), mEventTextPaint, availableWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
        }

        StaticLayout textLayout;

        // Calculate available number of line counts.
        int availableLineCount = availableHeight / lineHeight;

        do {
            // Ellipsize text to fit into event rect.
            textLayout = new StaticLayout(TextUtils.ellipsize(bob, mEventTextPaint, availableLineCount * availableWidth, TextUtils.TruncateAt.END), mEventTextPaint, availableWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);

            // Reduce line count.
            availableLineCount--;

            // Repeat until text is short enough.
        } while (textLayout.getHeight() > availableHeight);

        return textLayout;
    }

    /**
     * Get the time and date where the user clicked on.
     *
     * @param x The x position of the touch event.
     * @param y The y position of the touch event.
     * @return The time and date at the clicked position.
     */
    private Calendar getTimeFromPoint(float x, float y) {
        int leftDaysWithGaps = (int) -(Math.ceil(mCurrentOrigin.x / mWidthPerDay));
        float startPixel = mCurrentOrigin.x + mWidthPerDay * leftDaysWithGaps + mTimeColumnWidth;

        for (int dayNumber = leftDaysWithGaps + 1; dayNumber <= leftDaysWithGaps + mNumberOfVisibleDays + 1; dayNumber++) {
            float start = (startPixel < mTimeColumnWidth ? mTimeColumnWidth : startPixel);

            if (mWidthPerDay + startPixel - start > 0 && x > start && x < startPixel + mWidthPerDay) {
                Calendar day = today();
                day.add(Calendar.DATE, dayNumber - 1);
                float pixelsFromZero = y - mCurrentOrigin.y - mHeaderHeight;
                int hour = (int) (pixelsFromZero / mHourHeight);
                int minute = (int) (60 * (pixelsFromZero - hour * mHourHeight) / mHourHeight);
                day.add(Calendar.HOUR, hour);
                day.set(Calendar.MINUTE, minute);

                return day;
            }

            startPixel += mWidthPerDay;
        }

        return null;
    }
    // endregion

    // region Events methods

    /**
     * Gets more events of one/more month(s) if necessary. This method is called when the user is
     * scrolling the week view. The week view stores the events of three months: the visible month,
     * the previous month, the next month.
     *
     * @param day The day where the user is currently is.
     */
    private void getMoreEvents(Calendar day) {

        // Get more events if the month is changed.
        if (mEventRects == null) {
            mEventRects = new ArrayList<>();
        }

        if (mWeekViewLoader == null && !isInEditMode()) {
            throw new IllegalStateException("You must provide a MonthChangeListener");
        }

        // If a refresh was requested then reset some variables.
        if (mRefreshEvents) {
            mEventRects.clear();
            mPreviousPeriodEvents = null;
            mCurrentPeriodEvents = null;
            mNextPeriodEvents = null;
            mFetchedPeriod = -1;
        }

        if (mWeekViewLoader != null) {
            int periodToFetch = (int) mWeekViewLoader.toWeekViewPeriodIndex(day);

            if (!isInEditMode() && (mFetchedPeriod < 0 || mFetchedPeriod != periodToFetch || mRefreshEvents)) {
                List<? extends WeekViewEvent> previousPeriodEvents = null;
                List<? extends WeekViewEvent> currentPeriodEvents = null;
                List<? extends WeekViewEvent> nextPeriodEvents = null;

                if (mPreviousPeriodEvents != null && mCurrentPeriodEvents != null && mNextPeriodEvents != null) {
                    if (periodToFetch == mFetchedPeriod - 1) {
                        currentPeriodEvents = mPreviousPeriodEvents;
                        nextPeriodEvents = mCurrentPeriodEvents;
                    } else if (periodToFetch == mFetchedPeriod) {
                        previousPeriodEvents = mPreviousPeriodEvents;
                        currentPeriodEvents = mCurrentPeriodEvents;
                        nextPeriodEvents = mNextPeriodEvents;
                    } else if (periodToFetch == mFetchedPeriod + 1) {
                        previousPeriodEvents = mCurrentPeriodEvents;
                        currentPeriodEvents = mNextPeriodEvents;
                    }
                }

                if (currentPeriodEvents == null) {
                    currentPeriodEvents = mWeekViewLoader.onLoad(periodToFetch);
                }

                if (previousPeriodEvents == null) {
                    previousPeriodEvents = mWeekViewLoader.onLoad(periodToFetch - 1);
                }

                if (nextPeriodEvents == null) {
                    nextPeriodEvents = mWeekViewLoader.onLoad(periodToFetch + 1);
                }


                // Clear events.
                mEventRects.clear();
                sortAndCacheEvents(previousPeriodEvents);
                sortAndCacheEvents(currentPeriodEvents);
                sortAndCacheEvents(nextPeriodEvents);

                mPreviousPeriodEvents = previousPeriodEvents;
                mCurrentPeriodEvents = currentPeriodEvents;
                mNextPeriodEvents = nextPeriodEvents;
                mFetchedPeriod = periodToFetch;
            }
        }

        // Prepare to calculate positions of each events.
        List<EventRect> tempEvents = mEventRects;
        mEventRects = new ArrayList<EventRect>();

        // Iterate through each day with events to calculate the position of the events.
        while (tempEvents.size() > 0) {
            ArrayList<EventRect> eventRects = new ArrayList<>(tempEvents.size());

            // Get first event for a day.
            EventRect eventRect1 = tempEvents.remove(0);
            eventRects.add(eventRect1);

            int i = 0;

            while (i < tempEvents.size()) {
                // Collect all other events for same day.
                EventRect eventRect2 = tempEvents.get(i);
                if (isSameDay(eventRect1.event.getStartTime(), eventRect2.event.getStartTime())) {
                    tempEvents.remove(i);
                    eventRects.add(eventRect2);
                } else {
                    i++;
                }
            }

            computePositionOfEvents(eventRects);
        }
    }

    /**
     * Cache the event for smooth scrolling functionality.
     *
     * @param event The event to cache.
     */
    private void cacheEvent(WeekViewEvent event) {
        if (event.getStartTime().compareTo(event.getEndTime()) >= 0) {
            return;
        }

        List<WeekViewEvent> splitedEvents = event.splitWeekViewEvents();

        for (WeekViewEvent splitedEvent : splitedEvents) {
            mEventRects.add(new EventRect(splitedEvent, event, null));
        }
    }

    /**
     * Sort and cache events.
     *
     * @param events The events to be sorted and cached.
     */
    private void sortAndCacheEvents(List<? extends WeekViewEvent> events) {
        sortEvents(events);

        for (WeekViewEvent event : events) {
            cacheEvent(event);
        }
    }

    /**
     * Sorts the events in ascending order.
     *
     * @param events The events to be sorted.
     */
    private void sortEvents(List<? extends WeekViewEvent> events) {
        Collections.sort(events, new Comparator<WeekViewEvent>() {
            @Override
            public int compare(WeekViewEvent event1, WeekViewEvent event2) {
                long start1 = event1.getStartTime().getTimeInMillis();
                long start2 = event2.getStartTime().getTimeInMillis();
                int comparator = start1 > start2 ? 1 : (start1 < start2 ? -1 : 0);

                if (comparator == 0) {
                    long end1 = event1.getEndTime().getTimeInMillis();
                    long end2 = event2.getEndTime().getTimeInMillis();
                    comparator = end1 > end2 ? 1 : (end1 < end2 ? -1 : 0);
                }

                return comparator;
            }
        });
    }

    /**
     * Calculates the left and right positions of each events. This comes handy specially if events
     * are overlapping.
     *
     * @param eventRects The events along with their wrapper class.
     */
    private void computePositionOfEvents(List<EventRect> eventRects) {
        // Make "collision groups" for all events that collide with others.
        List<List<EventRect>> collisionGroups = new ArrayList<List<EventRect>>();

        for (EventRect eventRect : eventRects) {
            boolean isPlaced = false;

            outerLoop:
            for (List<EventRect> collisionGroup : collisionGroups) {
                for (EventRect groupEvent : collisionGroup) {
                    if (isEventsCollide(groupEvent.event, eventRect.event) && groupEvent.event.isAllDay() == eventRect.event.isAllDay()) {
                        collisionGroup.add(eventRect);
                        isPlaced = true;

                        break outerLoop;
                    }
                }
            }

            if (!isPlaced) {
                List<EventRect> newGroup = new ArrayList<EventRect>();
                newGroup.add(eventRect);
                collisionGroups.add(newGroup);
            }
        }

        for (List<EventRect> collisionGroup : collisionGroups) {
            expandEventsToMaxWidth(collisionGroup);
        }
    }

    /**
     * Expands all the events to maximum possible width. The events will try to occupy maximum
     * space available horizontally.
     *
     * @param collisionGroup The group of events which overlap with each other.
     */
    private void expandEventsToMaxWidth(List<EventRect> collisionGroup) {
        // Expand the events to maximum possible width.
        List<List<EventRect>> columns = new ArrayList<List<EventRect>>();
        columns.add(new ArrayList<EventRect>());

        for (EventRect eventRect : collisionGroup) {
            boolean isPlaced = false;

            for (List<EventRect> column : columns) {
                if (column.size() == 0) {
                    column.add(eventRect);
                    isPlaced = true;
                } else if (!isEventsCollide(eventRect.event, column.get(column.size() - 1).event)) {
                    column.add(eventRect);
                    isPlaced = true;

                    break;
                }
            }

            if (!isPlaced) {
                List<EventRect> newColumn = new ArrayList<EventRect>();
                newColumn.add(eventRect);
                columns.add(newColumn);
            }
        }


        // Calculate left and right position for all the events.
        // Get the maxRowCount by looking in all columns.
        int maxRowCount = 0;

        for (List<EventRect> column : columns) {
            maxRowCount = Math.max(maxRowCount, column.size());
        }

        for (int i = 0; i < maxRowCount; i++) {
            // Set the left and right values of the event.
            float j = 0;

            for (List<EventRect> column : columns) {
                if (column.size() >= i + 1) {
                    EventRect eventRect = column.get(i);
                    eventRect.width = 1f / columns.size();
                    eventRect.left = j / columns.size();

                    if (!eventRect.event.isAllDay()) {
                        eventRect.top = eventRect.event.getStartTime().get(Calendar.HOUR_OF_DAY) * 60 + eventRect.event.getStartTime().get(Calendar.MINUTE);
                        eventRect.bottom = eventRect.event.getEndTime().get(Calendar.HOUR_OF_DAY) * 60 + eventRect.event.getEndTime().get(Calendar.MINUTE);
                    } else {
                        eventRect.top = 0;
                        eventRect.bottom = mAllDayEventHeight - mGridThickness * 2 - mEventMargin * 2;
                    }

                    mEventRects.add(eventRect);
                }

                j++;
            }
        }
    }

    /**
     * Checks if two events overlap.
     *
     * @param event1 The first event.
     * @param event2 The second event.
     * @return true if the events overlap.
     */
    private boolean isEventsCollide(WeekViewEvent event1, WeekViewEvent event2) {
        long start1 = event1.getStartTime().getTimeInMillis();
        long end1 = event1.getEndTime().getTimeInMillis();
        long start2 = event2.getStartTime().getTimeInMillis();
        long end2 = event2.getEndTime().getTimeInMillis();

        return !((start1 >= end2) || (end1 <= start2));
    }
    // endregion

    // region Properties
    @Override
    public void invalidate() {
        super.invalidate();

        mAreDimensionsInvalid = true;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        mAreDimensionsInvalid = true;
    }

    public EventClickListener getEventClickListener() {
        return mEventClickListener;
    }

    public void setOnEventClickListener(EventClickListener listener) {
        this.mEventClickListener = listener;
    }

    @Nullable
    public MonthLoader.MonthChangeListener getMonthChangeListener() {
        if (mWeekViewLoader instanceof MonthLoader) {
            return ((MonthLoader) mWeekViewLoader).getOnMonthChangeListener();
        }

        return null;
    }

    public void setMonthChangeListener(MonthLoader.MonthChangeListener monthChangeListener) {
        this.mWeekViewLoader = new MonthLoader(monthChangeListener);
    }

    public EventLongPressListener getEventLongPressListener() {
        return mEventLongPressListener;
    }

    public void setEventLongPressListener(EventLongPressListener eventLongPressListener) {
        this.mEventLongPressListener = eventLongPressListener;
    }

    public void setEmptyViewClickListener(EmptyViewClickListener emptyViewClickListener) {
        this.mEmptyViewClickListener = emptyViewClickListener;
    }

    public EmptyViewClickListener getEmptyViewClickListener() {
        return mEmptyViewClickListener;
    }

    public void setEmptyViewLongPressListener(EmptyViewLongPressListener emptyViewLongPressListener) {
        this.mEmptyViewLongPressListener = emptyViewLongPressListener;
    }

    public EmptyViewLongPressListener getEmptyViewLongPressListener() {
        return mEmptyViewLongPressListener;
    }

    /**
     * Get the interpreter which provides the text to show in the header column and the header row.
     *
     * @return The date, time interpreter.
     */
    public DateTimeInterpreter getDateTimeInterpreter() {
        if (mDateTimeInterpreter == null) {
            throw new IllegalStateException("You must provide a DateTimeInterpreter");
        }

        return mDateTimeInterpreter;
    }

    /**
     * Set the interpreter which provides the text to show in the header column and the header row.
     *
     * @param dateTimeInterpreter The date, time interpreter.
     */
    public void setDateTimeInterpreter(DateTimeInterpreter dateTimeInterpreter) {
        this.mDateTimeInterpreter = dateTimeInterpreter;
    }
    // endregion

    // region Public methods

    /**
     * Show today on the week view.
     */
    public void goToToday() {
        Calendar today = Calendar.getInstance();
        goToDate(today);
    }

    /**
     * Show a specific day on the week view.
     *
     * @param date The date to show.
     */
    public void goToDate(Calendar date) {
        mScroller.forceFinished(true);
        mCurrentScrollDirection = mCurrentFlingDirection = Direction.NONE;

        date.set(Calendar.HOUR_OF_DAY, 0);
        date.set(Calendar.MINUTE, 0);
        date.set(Calendar.SECOND, 0);
        date.set(Calendar.MILLISECOND, 0);

        if (mAreDimensionsInvalid) {
            mScrollToDay = date;
            return;
        }

        mRefreshEvents = true;

        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);

        long day = 1000L * 60L * 60L * 24L;
        long dateInMillis = date.getTimeInMillis() + date.getTimeZone().getOffset(date.getTimeInMillis());
        long todayInMillis = today.getTimeInMillis() + today.getTimeZone().getOffset(today.getTimeInMillis());
        long dateDifference = (dateInMillis / day) - (todayInMillis / day);
        mCurrentOrigin.x = -dateDifference * mWidthPerDay;
        invalidate();
    }

    /**
     * Refreshes the view and loads the events again.
     */
    public void notifyDataSetChanged() {
        mRefreshEvents = true;
        invalidate();
    }

    /**
     * Vertically scroll to a specific hour in the week view.
     *
     * @param hour The hour to scroll to in 24-hour format. Supported values are 0-24.
     */
    public void goToHour(double hour) {
        if (mAreDimensionsInvalid) {
            mScrollToHour = hour;
            return;
        }

        int verticalOffset = 0;
        if (hour > 24)
            verticalOffset = mHourHeight * 24;
        else if (hour > 0)
            verticalOffset = (int) (mHourHeight * hour);

        if (verticalOffset > mHourHeight * 24 - getHeight() + mHeaderHeight + mHourHeight)
            verticalOffset = (int) (mHourHeight * 24 - getHeight() + mHeaderHeight + mHourHeight);

        mCurrentOrigin.y = -verticalOffset;
        invalidate();
    }

    /**
     * Get the first hour that is visible on the screen.
     *
     * @return The first hour that is visible.
     */
    public double getFirstVisibleHour() {
        return -mCurrentOrigin.y / mHourHeight;
    }
    // endregion

    // region Scrolling methods
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        mScaleDetector.onTouchEvent(event);
        boolean val = mGestureDetector.onTouchEvent(event);

        // Check after call of mGestureDetector, so mCurrentFlingDirection and mCurrentScrollDirection are set.
        if (event.getAction() == MotionEvent.ACTION_UP && !mIsZooming && mCurrentFlingDirection == Direction.NONE) {
            if (mCurrentScrollDirection == Direction.RIGHT || mCurrentScrollDirection == Direction.LEFT) {
                goToNearestOrigin();
            }

            mCurrentScrollDirection = Direction.NONE;
        }

        return val;
    }

    private void goToNearestOrigin() {
        double leftDays = mCurrentOrigin.x / mWidthPerDay;
        int scrollDuration = 250;

        if (mCurrentFlingDirection != Direction.NONE) {
            // snap to nearest day
            leftDays = Math.round(leftDays);
        } else if (mCurrentScrollDirection == Direction.LEFT) {
            // snap to last day
            leftDays = Math.floor(leftDays);
        } else if (mCurrentScrollDirection == Direction.RIGHT) {
            // snap to next day
            leftDays = Math.ceil(leftDays);
        } else {
            // snap to nearest day
            leftDays = Math.round(leftDays);
        }

        int nearestOrigin = (int) (mCurrentOrigin.x - leftDays * mWidthPerDay);

        if (nearestOrigin != 0) {
            // Stop current animation.
            mScroller.forceFinished(true);
            // Snap to date.
            mScroller.startScroll((int) mCurrentOrigin.x, (int) mCurrentOrigin.y, -nearestOrigin, 0, (int) (Math.abs(nearestOrigin) / mWidthPerDay * scrollDuration));
            ViewCompat.postInvalidateOnAnimation(WeekView.this);
        }

        // Reset scrolling and fling direction.
        mCurrentScrollDirection = mCurrentFlingDirection = Direction.NONE;
    }


    @Override
    public void computeScroll() {
        super.computeScroll();

        if (mScroller.isFinished()) {
            if (mCurrentFlingDirection != Direction.NONE) {
                // Snap to day after fling is finished.
                goToNearestOrigin();
            }
        } else {
            if (mCurrentFlingDirection != Direction.NONE && forceFinishScroll()) {
                goToNearestOrigin();
            } else if (mScroller.computeScrollOffset()) {
                mCurrentOrigin.y = mScroller.getCurrY();
                mCurrentOrigin.x = mScroller.getCurrX();
                ViewCompat.postInvalidateOnAnimation(this);
            }
        }
    }

    /**
     * Check if scrolling should be stopped.
     *
     * @return true if scrolling should be stopped before reaching the end of animation.
     */
    private boolean forceFinishScroll() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            // current velocity only available since api 14
            return mScroller.getCurrVelocity() <= mMinimumFlingVelocity;
        } else {
            return false;
        }
    }
    // endregion

    // region Interfaces

    public interface EventClickListener {
        /**
         * Triggered when clicked on one existing event
         *
         * @param event:     event clicked.
         * @param eventRect: view containing the clicked event.
         */
        void onEventClick(WeekViewEvent event, RectF eventRect);
    }

    public interface EventLongPressListener {
        /**
         * Similar to {@link com.alamkanak.weekview.WeekView.EventClickListener} but with a long press.
         *
         * @param event:     event clicked.
         * @param eventRect: view containing the clicked event.
         */
        void onEventLongPress(WeekViewEvent event, RectF eventRect);
    }

    public interface EmptyViewClickListener {
        /**
         * Triggered when the users clicks on a empty space of the calendar.
         *
         * @param time: {@link Calendar} object set with the date and time of the clicked position on the view.
         */
        void onEmptyViewClicked(Calendar time);
    }

    public interface EmptyViewLongPressListener {
        /**
         * Similar to {@link com.alamkanak.weekview.WeekView.EmptyViewClickListener} but with long press.
         *
         * @param time: {@link Calendar} object set with the date and time of the long pressed position on the view.
         */
        void onEmptyViewLongPress(Calendar time);
    }

    public interface ScrollListener {
        /**
         * Called when the first visible day has changed.
         * <p>
         * (this will also be called during the first draw of the weekview)
         *
         * @param newFirstVisibleDay The new first visible day
         * @param oldFirstVisibleDay The old first visible day (is null on the first call).
         */
        void onFirstVisibleDayChanged(Calendar newFirstVisibleDay, Calendar oldFirstVisibleDay);
    }
    // endregion

    // region Inner classes

    /**
     * A class to hold reference to the events and their visual representation. An EventRect is
     * actually the rectangle that is drawn on the calendar for a given event. There may be more
     * than one rectangle for a single event (an event that expands more than one day). In that
     * case two instances of the EventRect will be used for a single event. The given event will be
     * stored in "originalEvent". But the event that corresponds to rectangle the rectangle
     * instance will be stored in "event".
     */
    private class EventRect {
        WeekViewEvent event;
        WeekViewEvent originalEvent;
        RectF rectF;
        float left;
        float width;
        float top;
        float bottom;

        /**
         * Create a new instance of event rect. An EventRect is actually the rectangle that is drawn
         * on the calendar for a given event. There may be more than one rectangle for a single
         * event (an event that expands more than one day). In that case two instances of the
         * EventRect will be used for a single event. The given event will be stored in
         * "originalEvent". But the event that corresponds to rectangle the rectangle instance will
         * be stored in "event".
         *
         * @param event         Represents the event which this instance of rectangle represents.
         * @param originalEvent The original event that was passed by the user.
         * @param rectF         The rectangle.
         */
        EventRect(WeekViewEvent event, WeekViewEvent originalEvent, RectF rectF) {
            this.event = event;
            this.rectF = rectF;
            this.originalEvent = originalEvent;
        }
    }
    // endregion
}
