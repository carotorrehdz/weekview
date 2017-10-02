package com.alamkanak.weekview.sample;

import android.graphics.RectF;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.alamkanak.weekview.DateTimeInterpreter;
import com.alamkanak.weekview.MonthLoader;
import com.alamkanak.weekview.WeekView;
import com.alamkanak.weekview.WeekViewEvent;

import org.apache.commons.lang3.text.WordUtils;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * This is a base activity which contains week view and all the codes necessary to initialize the
 * week view.
 * Created by Raquib-ul-Alam Kanak on 1/3/2014.
 * Website: http://alamkanak.github.io
 */
public abstract class BaseActivity extends AppCompatActivity implements WeekView.EventClickListener, MonthLoader.MonthChangeListener, WeekView.EventLongPressListener, WeekView.EmptyViewLongPressListener, WeekView.EmptyViewClickListener, WeekView.ScrollListener, DateTimeInterpreter {

    private WeekView mWeekView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_base);

        mWeekView = (WeekView) findViewById(R.id.weekView);

        mWeekView.setOnEventClickListener(this);

        mWeekView.setMonthChangeListener(this);

        mWeekView.setEventLongPressListener(this);

        mWeekView.setEmptyViewLongPressListener(this);

        mWeekView.setEmptyViewClickListener(this);

        mWeekView.setScrollListener(this);

        mWeekView.setDateTimeInterpreter(this);

        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(1506747600000L);
        mWeekView.goToDate(calendar);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_today:
                mWeekView.goToToday();

                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public String interpretWeekday(Calendar date) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEE", Locale.getDefault());
        String weekday = dateFormat.format(date.getTime());

        return WordUtils.capitalize(weekday).replace(".", "");
    }

    @Override
    public String interpretDay(Calendar date) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("d", Locale.getDefault());

        return dateFormat.format(date.getTime());
    }

    @Override
    public String interpretTime(int hour) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.clear(Calendar.MINUTE);
        SimpleDateFormat dateFormat = new SimpleDateFormat("h:mm", Locale.ENGLISH);

        return dateFormat.format(calendar.getTime()).toLowerCase();
    }

    @Override
    public String interpretPeriod(int hour) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.clear(Calendar.MINUTE);
        SimpleDateFormat dateFormat = new SimpleDateFormat("a", Locale.ENGLISH);

        return dateFormat.format(calendar.getTime()).toLowerCase();
    }

    protected String getEventTitle(Calendar time) {
        return String.format("Event of %02d:%02d %s/%d", time.get(Calendar.HOUR_OF_DAY), time.get(Calendar.MINUTE), time.get(Calendar.MONTH) + 1, time.get(Calendar.DAY_OF_MONTH));
    }

    @Override
    public void onEventClick(WeekViewEvent event, RectF eventRect) {
        Toast.makeText(this, "Clicked " + event.getName(), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onEventLongPress(WeekViewEvent event, RectF eventRect) {
        Toast.makeText(this, "Long pressed event: " + event.getName(), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onEmptyViewClicked(Calendar time) {
        Toast.makeText(this, "Empty view: " + getEventTitle(time), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onEmptyViewLongPress(Calendar time) {
        Toast.makeText(this, "Empty view long pressed: " + getEventTitle(time), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onFirstVisibleDayChanged(Calendar newFirstVisibleDay, Calendar oldFirstVisibleDay) {
        if (newFirstVisibleDay == null || oldFirstVisibleDay == null) {
            return;
        }

        int newMonth = newFirstVisibleDay.get(Calendar.MONTH);
        int oldMonth = oldFirstVisibleDay.get(Calendar.MONTH);

        if (newMonth == oldMonth) {
            return;
        }

        SimpleDateFormat dateFormat = new SimpleDateFormat("MMMM", Locale.getDefault());
        setTitle(dateFormat.format(newFirstVisibleDay.getTime()));
    }

    public WeekView getWeekView() {
        return mWeekView;
    }
}
