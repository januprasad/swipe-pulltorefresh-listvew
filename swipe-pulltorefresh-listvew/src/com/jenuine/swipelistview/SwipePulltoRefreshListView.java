
package com.jenuine.swipelistview;

import android.content.Context;
import android.content.res.TypedArray;
import android.database.DataSetObserver;
import android.support.v4.view.MotionEventCompat;
import android.support.v4.view.ViewConfigurationCompat;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.ViewGroup.MarginLayoutParams;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.animation.RotateAnimation;
import android.view.animation.TranslateAnimation;
import android.view.animation.Animation.AnimationListener;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;


/**
 * ListView subclass that provides the swipe functionality
 */
public class SwipePulltoRefreshListView extends ListView {

	
	
	
	


    private static final float PULL_RESISTANCE                 = 1.7f;
    private static final int   BOUNCE_ANIMATION_DURATION       = 700;
    private static final int   BOUNCE_ANIMATION_DELAY          = 100;
    private static final float BOUNCE_OVERSHOOT_TENSION        = 1.4f;
    private static final int   ROTATE_ARROW_ANIMATION_DURATION = 250;

    private static enum State{
        PULL_TO_REFRESH,
        RELEASE_TO_REFRESH,
        REFRESHING
    }

    /**
     * Interface to implement when you want to get notified of 'pull to refresh'
     * events.
     * Call setOnRefreshListener(..) to activate an OnRefreshListener.
     */
    public interface OnRefreshListener{

        /**
         * Method to be called when a refresh is requested
         */
        public void onRefresh();
    }

    private static int measuredHeaderHeight;

    private boolean scrollbarEnabled;
    private boolean bounceBackHeader;
    private boolean lockScrollWhileRefreshing;
    private boolean showLastUpdatedText;
    private String  pullToRefreshText;
    private String  releaseToRefreshText;
    private String  refreshingText;
    private String  lastUpdatedText;
    private SimpleDateFormat lastUpdatedDateFormat = new SimpleDateFormat("dd/MM HH:mm");

    private float                   previousY;
    private int                     headerPadding;
    private boolean                 hasResetHeader;
    private long                    lastUpdated = -1;
    private State                   state;
    private LinearLayout            headerContainer;
    private RelativeLayout          header;
    private RotateAnimation         flipAnimation;
    private RotateAnimation         reverseFlipAnimation;
    private ImageView               image;
    private ProgressBar             spinner;
    private TextView                text;
    private TextView                lastUpdatedTextView;
    private OnItemClickListener     onItemClickListener;
    private OnItemLongClickListener onItemLongClickListener;
    private OnRefreshListener       onRefreshListener;

    private float mScrollStartY;
    private final int IDLE_DISTANCE = 5;

    public SwipePulltoRefreshListView(Context context){
        super(context);
        init();
    }

//    public SwipeListView(Context context, AttributeSet attrs){
//        super(context, attrs);
//        init();
//    }
//
//    public SwipeListView(Context context, AttributeSet attrs, int defStyle){
//        super(context, attrs, defStyle);
//        init();
//    }

    @Override
    public void setOnItemClickListener(OnItemClickListener onItemClickListener){
        this.onItemClickListener = onItemClickListener;
    }

    @Override
    public void setOnItemLongClickListener(OnItemLongClickListener onItemLongClickListener){
        this.onItemLongClickListener = onItemLongClickListener;
    }

    /**
     * Activate an OnRefreshListener to get notified on 'pull to refresh'
     * events.
     *
     * @param onRefreshListener The OnRefreshListener to get notified
     */
    public void setOnRefreshListener(OnRefreshListener onRefreshListener){
        this.onRefreshListener = onRefreshListener;
    }

    /**
     * @return If the list is in 'Refreshing' state
     */
    public boolean isRefreshing(){
        return state == State.REFRESHING;
    }

    /**
     * Default is false. When lockScrollWhileRefreshing is set to true, the list
     * cannot scroll when in 'refreshing' mode. It's 'locked' on refreshing.
     *
     * @param lockScrollWhileRefreshing
     */
    public void setLockScrollWhileRefreshing(boolean lockScrollWhileRefreshing){
        this.lockScrollWhileRefreshing = lockScrollWhileRefreshing;
    }

    /**
     * Default is false. Show the last-updated date/time in the 'Pull ro Refresh'
     * header. See 'setLastUpdatedDateFormat' to set the date/time formatting.
     *
     * @param showLastUpdatedText
     */
    public void setShowLastUpdatedText(boolean showLastUpdatedText){
        this.showLastUpdatedText = showLastUpdatedText;
        if(!showLastUpdatedText) lastUpdatedTextView.setVisibility(View.GONE);
    }

    /**
     * Default: "dd/MM HH:mm". Set the format in which the last-updated
     * date/time is shown. Meaningless if 'showLastUpdatedText == false (default)'.
     * See 'setShowLastUpdatedText'.
     *
     * @param lastUpdatedDateFormat
     */
    public void setLastUpdatedDateFormat(SimpleDateFormat lastUpdatedDateFormat){
        this.lastUpdatedDateFormat = lastUpdatedDateFormat;
    }

    /**
     * Explicitly set the state to refreshing. This
     * is useful when you want to show the spinner and 'Refreshing' text when
     * the refresh was not triggered by 'pull to refresh', for example on start.
     */
    public void setRefreshing(){
        state = State.REFRESHING;
        scrollTo(0, 0);
        setUiRefreshing();
        setHeaderPadding(0);
    }

    /**
     * Set the state back to 'pull to refresh'. Call this method when refreshing
     * the data is finished.
     */
    public void onRefreshComplete(){
        state = State.PULL_TO_REFRESH;
        resetHeader();
        lastUpdated = System.currentTimeMillis();
    }

    /**
     * Change the label text on state 'Pull to Refresh'
     *
     * @param pullToRefreshText Text
     */
    public void setTextPullToRefresh(String pullToRefreshText){
        this.pullToRefreshText = pullToRefreshText;
        if(state == State.PULL_TO_REFRESH){
            text.setText(pullToRefreshText);
        }
    }

    /**
     * Change the label text on state 'Release to Refresh'
     *
     * @param releaseToRefreshText Text
     */
    public void setTextReleaseToRefresh(String releaseToRefreshText){
        this.releaseToRefreshText = releaseToRefreshText;
        if(state == State.RELEASE_TO_REFRESH){
            text.setText(releaseToRefreshText);
        }
    }

    /**
     * Change the label text on state 'Refreshing'
     *
     * @param refreshingText Text
     */
    public void setTextRefreshing(String refreshingText){
        this.refreshingText = refreshingText;
        if(state == State.REFRESHING){
            text.setText(refreshingText);
        }
    }

    private void init(){
        setVerticalFadingEdgeEnabled(false);

        headerContainer = (LinearLayout) LayoutInflater.from(getContext()).inflate(R.layout.ptr_header, null);
        header = (RelativeLayout) headerContainer.findViewById(R.id.ptr_id_header);
        text = (TextView) header.findViewById(R.id.ptr_id_text);
        lastUpdatedTextView = (TextView) header.findViewById(R.id.ptr_id_last_updated);
        image = (ImageView) header.findViewById(R.id.ptr_id_image);
        spinner = (ProgressBar) header.findViewById(R.id.ptr_id_spinner);

        pullToRefreshText = getContext().getString(R.string.ptr_pull_to_refresh);
        releaseToRefreshText = getContext().getString(R.string.ptr_release_to_refresh);
        refreshingText = getContext().getString(R.string.ptr_refreshing);
        lastUpdatedText = getContext().getString(R.string.ptr_last_updated);

        flipAnimation = new RotateAnimation(0, -180, RotateAnimation.RELATIVE_TO_SELF, 0.5f, RotateAnimation.RELATIVE_TO_SELF, 0.5f);
        flipAnimation.setInterpolator(new LinearInterpolator());
        flipAnimation.setDuration(ROTATE_ARROW_ANIMATION_DURATION);
        flipAnimation.setFillAfter(true);

        reverseFlipAnimation = new RotateAnimation(-180, 0, RotateAnimation.RELATIVE_TO_SELF, 0.5f, RotateAnimation.RELATIVE_TO_SELF, 0.5f);
        reverseFlipAnimation.setInterpolator(new LinearInterpolator());
        reverseFlipAnimation.setDuration(ROTATE_ARROW_ANIMATION_DURATION);
        reverseFlipAnimation.setFillAfter(true);

        addHeaderView(headerContainer);
        setState(State.PULL_TO_REFRESH);
        scrollbarEnabled = isVerticalScrollBarEnabled();

        ViewTreeObserver vto = header.getViewTreeObserver();
        vto.addOnGlobalLayoutListener(new PTROnGlobalLayoutListener());

        super.setOnItemClickListener(new PTROnItemClickListener());
        super.setOnItemLongClickListener(new PTROnItemLongClickListener());
    }

    private void setHeaderPadding(int padding){
        headerPadding = padding;

        MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) header.getLayoutParams();
        mlp.setMargins(0, Math.round(padding), 0, 0);
        header.setLayoutParams(mlp);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event){
        if(lockScrollWhileRefreshing
                && (state == State.REFRESHING || getAnimation() != null && !getAnimation().hasEnded())){
            return true;
        }

        switch(event.getAction()){
            case MotionEvent.ACTION_DOWN:
                if(getFirstVisiblePosition() == 0){
                	previousY = event.getY();
                }
                else {
                	previousY = -1;
                }
                
                // Remember where have we started
                mScrollStartY = event.getY();
                
                break;

            case MotionEvent.ACTION_UP:
                if(previousY != -1 && (state == State.RELEASE_TO_REFRESH || getFirstVisiblePosition() == 0)){
                    switch(state){
                        case RELEASE_TO_REFRESH:
                            setState(State.REFRESHING);
                            bounceBackHeader();

                            break;

                        case PULL_TO_REFRESH:
                            resetHeader();
                            break;
                    }
                }
                break;

            case MotionEvent.ACTION_MOVE:
                if(previousY != -1 && getFirstVisiblePosition() == 0 && Math.abs(mScrollStartY-event.getY()) > IDLE_DISTANCE){
                    float y = event.getY();
                    float diff = y - previousY;
                    if(diff > 0) diff /= PULL_RESISTANCE;
                    previousY = y;

                    int newHeaderPadding = Math.max(Math.round(headerPadding + diff), -header.getHeight());

                    if(newHeaderPadding != headerPadding && state != State.REFRESHING){
                        setHeaderPadding(newHeaderPadding);

                        if(state == State.PULL_TO_REFRESH && headerPadding > 0){
                            setState(State.RELEASE_TO_REFRESH);

                            image.clearAnimation();
                            image.startAnimation(flipAnimation);
                        }else if(state == State.RELEASE_TO_REFRESH && headerPadding < 0){
                            setState(State.PULL_TO_REFRESH);

                            image.clearAnimation();
                            image.startAnimation(reverseFlipAnimation);
                        }
                    }
                }

                break;
        }

        return super.onTouchEvent(event);
    }

    private void bounceBackHeader(){
        int yTranslate = state == State.REFRESHING ?
                header.getHeight() - headerContainer.getHeight() :
                -headerContainer.getHeight() - headerContainer.getTop() + getPaddingTop();;

        TranslateAnimation bounceAnimation = new TranslateAnimation(
                TranslateAnimation.ABSOLUTE, 0,
                TranslateAnimation.ABSOLUTE, 0,
                TranslateAnimation.ABSOLUTE, 0,
                TranslateAnimation.ABSOLUTE, yTranslate);

        bounceAnimation.setDuration(BOUNCE_ANIMATION_DURATION);
        bounceAnimation.setFillEnabled(true);
        bounceAnimation.setFillAfter(false);
        bounceAnimation.setFillBefore(true);
        bounceAnimation.setInterpolator(new OvershootInterpolator(BOUNCE_OVERSHOOT_TENSION));
        bounceAnimation.setAnimationListener(new HeaderAnimationListener(yTranslate));

        startAnimation(bounceAnimation);
    }

    private void resetHeader(){
        if(getFirstVisiblePosition() > 0){
            setHeaderPadding(-header.getHeight());
            setState(State.PULL_TO_REFRESH);
            return;
        }

        if(getAnimation() != null && !getAnimation().hasEnded()){
            bounceBackHeader = true;
        }else{
            bounceBackHeader();
        }
    }

    private void setUiRefreshing(){
        spinner.setVisibility(View.VISIBLE);
        image.clearAnimation();
        image.setVisibility(View.INVISIBLE);
        text.setText(refreshingText);
    }

    private void setState(State state){
        this.state = state;
        switch(state){
            case PULL_TO_REFRESH:
                spinner.setVisibility(View.INVISIBLE);
                image.setVisibility(View.VISIBLE);
                text.setText(pullToRefreshText);

                if(showLastUpdatedText && lastUpdated != -1){
                    lastUpdatedTextView.setVisibility(View.VISIBLE);
                    lastUpdatedTextView.setText(String.format(lastUpdatedText, lastUpdatedDateFormat.format(new Date(lastUpdated))));
                }

                break;

            case RELEASE_TO_REFRESH:
                spinner.setVisibility(View.INVISIBLE);
                image.setVisibility(View.VISIBLE);
                text.setText(releaseToRefreshText);
                break;

            case REFRESHING:
                setUiRefreshing();

                lastUpdated = System.currentTimeMillis();
                if(onRefreshListener == null){
                    setState(State.PULL_TO_REFRESH);
                }else{
                    onRefreshListener.onRefresh();
                }

                break;
        }
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt){
        super.onScrollChanged(l, t, oldl, oldt);

        if(!hasResetHeader){
            if(measuredHeaderHeight > 0 && state != State.REFRESHING){
                setHeaderPadding(-measuredHeaderHeight);
            }

            hasResetHeader = true;
        }
    }

    private class HeaderAnimationListener implements AnimationListener{

        private int height, translation;
        private State stateAtAnimationStart;

        public HeaderAnimationListener(int translation){
            this.translation = translation;
        }

        @Override
        public void onAnimationStart(Animation animation){
            stateAtAnimationStart = state;

            android.view.ViewGroup.LayoutParams lp = getLayoutParams();
            height = lp.height;
            lp.height = getHeight() - translation;
            setLayoutParams(lp);

            if(scrollbarEnabled){
                setVerticalScrollBarEnabled(false);
            }
        }

        @Override
        public void onAnimationEnd(Animation animation){
            setHeaderPadding(stateAtAnimationStart == State.REFRESHING ? 0 : -measuredHeaderHeight - headerContainer.getTop());
            setSelection(0);

            android.view.ViewGroup.LayoutParams lp = getLayoutParams();
            lp.height = height;
            setLayoutParams(lp);

            if(scrollbarEnabled){
                setVerticalScrollBarEnabled(true);
            }

            if(bounceBackHeader){
                bounceBackHeader = false;

                postDelayed(new Runnable(){

                    @Override
                    public void run(){
                        resetHeader();
                    }
                }, BOUNCE_ANIMATION_DELAY);
            }else if(stateAtAnimationStart != State.REFRESHING){
                setState(State.PULL_TO_REFRESH);
            }
        }

        @Override
        public void onAnimationRepeat(Animation animation){}
    }

    private class PTROnGlobalLayoutListener implements OnGlobalLayoutListener{

        @Override
        public void onGlobalLayout(){
            int initialHeaderHeight = header.getHeight();

            if(initialHeaderHeight > 0){
                measuredHeaderHeight = initialHeaderHeight;

                if(measuredHeaderHeight > 0 && state != State.REFRESHING){
                    setHeaderPadding(-measuredHeaderHeight);
                    requestLayout();
                }
            }

            getViewTreeObserver().removeGlobalOnLayoutListener(this);
        }
    }

    private class PTROnItemClickListener implements OnItemClickListener{

        @Override
        public void onItemClick(AdapterView<?> adapterView, View view, int position, long id){
            hasResetHeader = false;

            if(onItemClickListener != null && state == State.PULL_TO_REFRESH){
                // Passing up onItemClick. Correct position with the number of header views
                onItemClickListener.onItemClick(adapterView, view, position - getHeaderViewsCount(), id);
            }
        }
    }

    private class PTROnItemLongClickListener implements OnItemLongClickListener{

        @Override
        public boolean onItemLongClick(AdapterView<?> adapterView, View view, int position, long id){
            hasResetHeader = false;

            if(onItemLongClickListener != null && state == State.PULL_TO_REFRESH){
                // Passing up onItemLongClick. Correct position with the number of header views
                return onItemLongClickListener.onItemLongClick(adapterView, view, position - getHeaderViewsCount(), id);
            }

            return false;
        }
    }

	
	/*-------------------------------*/
	
	
	
	
    /**
     * Used when user want change swipe list mode on some rows
     */
    public final static int SWIPE_MODE_DEFAULT = -1;

    /**
     * Disables all swipes
     */
    public final static int SWIPE_MODE_NONE = 0;

    /**
     * Enables both left and right swipe
     */
    public final static int SWIPE_MODE_BOTH = 1;

    /**
     * Enables right swipe
     */
    public final static int SWIPE_MODE_RIGHT = 2;

    /**
     * Enables left swipe
     */
    public final static int SWIPE_MODE_LEFT = 3;

    /**
     * Binds the swipe gesture to reveal a view behind the row (Drawer style)
     */
    public final static int SWIPE_ACTION_REVEAL = 0;

    /**
     * Dismisses the cell when swiped over
     */
    public final static int SWIPE_ACTION_DISMISS = 1;

    /**
     * Marks the cell as checked when swiped and release
     */
    public final static int SWIPE_ACTION_CHOICE = 2;

    /**
     * No action when swiped
     */
    public final static int SWIPE_ACTION_NONE = 3;

    /**
     * Default ids for front view
     */
    public final static String SWIPE_DEFAULT_FRONT_VIEW = "swipelist_frontview";

    /**
     * Default id for back view
     */
    public final static String SWIPE_DEFAULT_BACK_VIEW = "swipelist_backview";

    /**
     * Indicates no movement
     */
    private final static int TOUCH_STATE_REST = 0;

    /**
     * State scrolling x position
     */
    private final static int TOUCH_STATE_SCROLLING_X = 1;

    /**
     * State scrolling y position
     */
    private final static int TOUCH_STATE_SCROLLING_Y = 2;

    private int touchState = TOUCH_STATE_REST;

    private float lastMotionX;
    private float lastMotionY;
    private int touchSlop;

    int swipeFrontView = 0;
    int swipeBackView = 0;

    /**
     * Internal listener for common swipe events
     */
    private SwipeListViewListener swipeListViewListener;

    /**
     * Internal touch listener
     */
    private SwipeListViewTouchListener touchListener;


    /**
     * If you create a View programmatically you need send back and front identifier
     *
     * @param context        Context
     * @param swipeBackView  Back Identifier
     * @param swipeFrontView Front Identifier
     */
    public SwipePulltoRefreshListView(Context context, int swipeBackView, int swipeFrontView) {
        super(context);
        this.swipeFrontView = swipeFrontView;
        this.swipeBackView = swipeBackView;
        init(null);
        init();
    }

    /**
     * @see android.widget.ListView#ListView(android.content.Context, android.util.AttributeSet)
     */
    public SwipePulltoRefreshListView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
        init();
    }

    /**
     * @see android.widget.ListView#ListView(android.content.Context, android.util.AttributeSet, int)
     */
    public SwipePulltoRefreshListView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(attrs);
    }

    /**
     * Init ListView
     *
     * @param attrs AttributeSet
     */
    private void init(AttributeSet attrs) {

        int swipeMode = SWIPE_MODE_BOTH;
        boolean swipeOpenOnLongPress = true;
        boolean swipeCloseAllItemsWhenMoveList = true;
        long swipeAnimationTime = 0;
        float swipeOffsetLeft = 0;
        float swipeOffsetRight = 0;
        int swipeDrawableChecked = 0;
        int swipeDrawableUnchecked = 0;

        int swipeActionLeft = SWIPE_ACTION_REVEAL;
        int swipeActionRight = SWIPE_ACTION_REVEAL;

        if (attrs != null) {
            TypedArray styled = getContext().obtainStyledAttributes(attrs, R.styleable.SwipePulltoRefreshListView);
            swipeMode = styled.getInt(R.styleable.SwipePulltoRefreshListView_swipeMode, SWIPE_MODE_BOTH);
            swipeActionLeft = styled.getInt(R.styleable.SwipePulltoRefreshListView_swipeActionLeft, SWIPE_ACTION_REVEAL);
            swipeActionRight = styled.getInt(R.styleable.SwipePulltoRefreshListView_swipeActionRight, SWIPE_ACTION_REVEAL);
            swipeOffsetLeft = styled.getDimension(R.styleable.SwipePulltoRefreshListView_swipeOffsetLeft, 0);
            swipeOffsetRight = styled.getDimension(R.styleable.SwipePulltoRefreshListView_swipeOffsetRight, 0);
            swipeOpenOnLongPress = styled.getBoolean(R.styleable.SwipePulltoRefreshListView_swipeOpenOnLongPress, true);
            swipeAnimationTime = styled.getInteger(R.styleable.SwipePulltoRefreshListView_swipeAnimationTime, 0);
            swipeCloseAllItemsWhenMoveList = styled.getBoolean(R.styleable.SwipePulltoRefreshListView_swipeCloseAllItemsWhenMoveList, true);
            swipeDrawableChecked = styled.getResourceId(R.styleable.SwipePulltoRefreshListView_swipeDrawableChecked, 0);
            swipeDrawableUnchecked = styled.getResourceId(R.styleable.SwipePulltoRefreshListView_swipeDrawableUnchecked, 0);
            swipeFrontView = styled.getResourceId(R.styleable.SwipePulltoRefreshListView_swipeFrontView, 0);
            swipeBackView = styled.getResourceId(R.styleable.SwipePulltoRefreshListView_swipeBackView, 0);
        }

        if (swipeFrontView == 0 || swipeBackView == 0) {
            swipeFrontView = getContext().getResources().getIdentifier(SWIPE_DEFAULT_FRONT_VIEW, "id", getContext().getPackageName());
            swipeBackView = getContext().getResources().getIdentifier(SWIPE_DEFAULT_BACK_VIEW, "id", getContext().getPackageName());

            if (swipeFrontView == 0 || swipeBackView == 0) {
                throw new RuntimeException(String.format("You forgot the attributes swipeFrontView or swipeBackView. You can add this attributes or use '%s' and '%s' identifiers", SWIPE_DEFAULT_FRONT_VIEW, SWIPE_DEFAULT_BACK_VIEW));
            }
        }

        final ViewConfiguration configuration = ViewConfiguration.get(getContext());
        touchSlop = ViewConfigurationCompat.getScaledPagingTouchSlop(configuration);
        touchListener = new SwipeListViewTouchListener(this, swipeFrontView, swipeBackView);
        if (swipeAnimationTime > 0) {
            touchListener.setAnimationTime(swipeAnimationTime);
        }
        touchListener.setRightOffset(swipeOffsetRight);
        touchListener.setLeftOffset(swipeOffsetLeft);
        touchListener.setSwipeActionLeft(swipeActionLeft);
        touchListener.setSwipeActionRight(swipeActionRight);
        touchListener.setSwipeMode(swipeMode);
        touchListener.setSwipeClosesAllItemsWhenListMoves(swipeCloseAllItemsWhenMoveList);
        touchListener.setSwipeOpenOnLongPress(swipeOpenOnLongPress);
        touchListener.setSwipeDrawableChecked(swipeDrawableChecked);
        touchListener.setSwipeDrawableUnchecked(swipeDrawableUnchecked);
        setOnTouchListener(touchListener);
        setOnScrollListener(touchListener.makeScrollListener());
    }

    /**
     * Recycle cell. This method should be called from getView in Adapter when use SWIPE_ACTION_CHOICE
     *
     * @param convertView parent view
     * @param position    position in list
     */
    public void recycle(View convertView, int position) {
        touchListener.reloadChoiceStateInView(convertView.findViewById(swipeFrontView), position);
    }

    /**
     * Get if item is selected
     *
     * @param position position in list
     * @return
     */
    public boolean isChecked(int position) {
        return touchListener.isChecked(position);
    }

    /**
     * Get positions selected
     *
     * @return
     */
    public List<Integer> getPositionsSelected() {
        return touchListener.getPositionsSelected();
    }

    /**
     * Count selected
     *
     * @return
     */
    public int getCountSelected() {
        return touchListener.getCountSelected();
    }

    /**
     * Unselected choice state in item
     */
    public void unselectedChoiceStates() {
        touchListener.unselectedChoiceStates();
    }

    /**
     * @see android.widget.ListView#setAdapter(android.widget.ListAdapter)
     */
    @Override
    public void setAdapter(ListAdapter adapter) {
        super.setAdapter(adapter);
        touchListener.resetItems();
        adapter.registerDataSetObserver(new DataSetObserver() {
            @Override
            public void onChanged() {
                super.onChanged();
                onListChanged();
                touchListener.resetItems();
            }
        });
    }

    /**
     * Dismiss item
     *
     * @param position Position that you want open
     */
    public void dismiss(int position) {
        int height = touchListener.dismiss(position);
        if (height > 0) {
            touchListener.handlerPendingDismisses(height);
        } else {
            int[] dismissPositions = new int[1];
            dismissPositions[0] = position;
            onDismiss(dismissPositions);
            touchListener.resetPendingDismisses();
        }
    }

    /**
     * Dismiss items selected
     */
    public void dismissSelected() {
        List<Integer> list = touchListener.getPositionsSelected();
        int[] dismissPositions = new int[list.size()];
        int height = 0;
        for (int i = 0; i < list.size(); i++) {
            int position = list.get(i);
            dismissPositions[i] = position;
            int auxHeight = touchListener.dismiss(position);
            if (auxHeight > 0) {
                height = auxHeight;
            }
        }
        if (height > 0) {
            touchListener.handlerPendingDismisses(height);
        } else {
            onDismiss(dismissPositions);
            touchListener.resetPendingDismisses();
        }
        touchListener.returnOldActions();
    }

    /**
     * Open ListView's item
     *
     * @param position Position that you want open
     */
    public void openAnimate(int position) {
        touchListener.openAnimate(position);
    }

    /**
     * Close ListView's item
     *
     * @param position Position that you want open
     */
    public void closeAnimate(int position) {
        touchListener.closeAnimate(position);
    }

    /**
     * Notifies onDismiss
     *
     * @param reverseSortedPositions All dismissed positions
     */
    protected void onDismiss(int[] reverseSortedPositions) {
        if (swipeListViewListener != null) {
            swipeListViewListener.onDismiss(reverseSortedPositions);
        }
    }

    /**
     * Start open item
     *
     * @param position list item
     * @param action   current action
     * @param right    to right
     */
    protected void onStartOpen(int position, int action, boolean right) {
        if (swipeListViewListener != null && position != ListView.INVALID_POSITION) {
            swipeListViewListener.onStartOpen(position, action, right);
        }
    }

    /**
     * Start close item
     *
     * @param position list item
     * @param right
     */
    protected void onStartClose(int position, boolean right) {
        if (swipeListViewListener != null && position != ListView.INVALID_POSITION) {
            swipeListViewListener.onStartClose(position, right);
        }
    }

    /**
     * Notifies onClickFrontView
     *
     * @param position item clicked
     */
    protected void onClickFrontView(int position) {
        if (swipeListViewListener != null && position != ListView.INVALID_POSITION) {
            swipeListViewListener.onClickFrontView(position);
        }
    }

    /**
     * Notifies onClickBackView
     *
     * @param position back item clicked
     */
    protected void onClickBackView(int position) {
        if (swipeListViewListener != null && position != ListView.INVALID_POSITION) {
            swipeListViewListener.onClickBackView(position);
        }
    }

    /**
     * Notifies onOpened
     *
     * @param position Item opened
     * @param toRight  If should be opened toward the right
     */
    protected void onOpened(int position, boolean toRight) {
        if (swipeListViewListener != null && position != ListView.INVALID_POSITION) {
            swipeListViewListener.onOpened(position, toRight);
        }
    }

    /**
     * Notifies onClosed
     *
     * @param position  Item closed
     * @param fromRight If open from right
     */
    protected void onClosed(int position, boolean fromRight) {
        if (swipeListViewListener != null && position != ListView.INVALID_POSITION) {
            swipeListViewListener.onClosed(position, fromRight);
        }
    }

    /**
     * Notifies onChoiceChanged
     *
     * @param position position that choice
     * @param selected if item is selected or not
     */
    protected void onChoiceChanged(int position, boolean selected) {
        if (swipeListViewListener != null && position != ListView.INVALID_POSITION) {
            swipeListViewListener.onChoiceChanged(position, selected);
        }
    }

    /**
     * User start choice items
     */
    protected void onChoiceStarted() {
        if (swipeListViewListener != null) {
            swipeListViewListener.onChoiceStarted();
        }
    }

    /**
     * User end choice items
     */
    protected void onChoiceEnded() {
        if (swipeListViewListener != null) {
            swipeListViewListener.onChoiceEnded();
        }
    }

    /**
     * User is in first item of list
     */
    protected void onFirstListItem() {
        if (swipeListViewListener != null) {
            swipeListViewListener.onFirstListItem();
        }
    }

    /**
     * User is in last item of list
     */
    protected void onLastListItem() {
        if (swipeListViewListener != null) {
            swipeListViewListener.onLastListItem();
        }
    }

    /**
     * Notifies onListChanged
     */
    protected void onListChanged() {
        if (swipeListViewListener != null) {
            swipeListViewListener.onListChanged();
        }
    }

    /**
     * Notifies onMove
     *
     * @param position Item moving
     * @param x        Current position
     */
    protected void onMove(int position, float x) {
        if (swipeListViewListener != null && position != ListView.INVALID_POSITION) {
            swipeListViewListener.onMove(position, x);
        }
    }

    protected int changeSwipeMode(int position) {
        if (swipeListViewListener != null && position != ListView.INVALID_POSITION) {
            return swipeListViewListener.onChangeSwipeMode(position);
        }
        return SWIPE_MODE_DEFAULT;
    }

    /**
     * Sets the Listener
     *
     * @param swipeListViewListener Listener
     */
    public void setSwipeListViewListener(SwipeListViewListener swipeListViewListener) {
        this.swipeListViewListener = swipeListViewListener;
    }

    /**
     * Resets scrolling
     */
    public void resetScrolling() {
        touchState = TOUCH_STATE_REST;
    }

    /**
     * Set offset on right
     *
     * @param offsetRight Offset
     */
    public void setOffsetRight(float offsetRight) {
        touchListener.setRightOffset(offsetRight);
    }

    /**
     * Set offset on left
     *
     * @param offsetLeft Offset
     */
    public void setOffsetLeft(float offsetLeft) {
        touchListener.setLeftOffset(offsetLeft);
    }

    /**
     * Set if all items opened will be closed when the user moves the ListView
     *
     * @param swipeCloseAllItemsWhenMoveList
     */
    public void setSwipeCloseAllItemsWhenMoveList(boolean swipeCloseAllItemsWhenMoveList) {
        touchListener.setSwipeClosesAllItemsWhenListMoves(swipeCloseAllItemsWhenMoveList);
    }

    /**
     * Sets if the user can open an item with long pressing on cell
     *
     * @param swipeOpenOnLongPress
     */
    public void setSwipeOpenOnLongPress(boolean swipeOpenOnLongPress) {
        touchListener.setSwipeOpenOnLongPress(swipeOpenOnLongPress);
    }

    /**
     * Set swipe mode
     *
     * @param swipeMode
     */
    public void setSwipeMode(int swipeMode) {
        touchListener.setSwipeMode(swipeMode);
    }

    /**
     * Return action on left
     *
     * @return Action
     */
    public int getSwipeActionLeft() {
        return touchListener.getSwipeActionLeft();
    }

    /**
     * Set action on left
     *
     * @param swipeActionLeft Action
     */
    public void setSwipeActionLeft(int swipeActionLeft) {
        touchListener.setSwipeActionLeft(swipeActionLeft);
    }

    /**
     * Return action on right
     *
     * @return Action
     */
    public int getSwipeActionRight() {
        return touchListener.getSwipeActionRight();
    }

    /**
     * Set action on right
     *
     * @param swipeActionRight Action
     */
    public void setSwipeActionRight(int swipeActionRight) {
        touchListener.setSwipeActionRight(swipeActionRight);
    }

    /**
     * Sets animation time when user drops cell
     *
     * @param animationTime milliseconds
     */
    public void setAnimationTime(long animationTime) {
        touchListener.setAnimationTime(animationTime);
    }

    /**
     * @see android.widget.ListView#onInterceptTouchEvent(android.view.MotionEvent)
     */
    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        int action = MotionEventCompat.getActionMasked(ev);
        final float x = ev.getX();
        final float y = ev.getY();

        if (isEnabled() && touchListener.isSwipeEnabled()) {

            if (touchState == TOUCH_STATE_SCROLLING_X) {
                return touchListener.onTouch(this, ev);
            }

            switch (action) {
                case MotionEvent.ACTION_MOVE:
                    checkInMoving(x, y);
                    return touchState == TOUCH_STATE_SCROLLING_Y;
                case MotionEvent.ACTION_DOWN:
                    super.onInterceptTouchEvent(ev);
                    touchListener.onTouch(this, ev);
                    touchState = TOUCH_STATE_REST;
                    lastMotionX = x;
                    lastMotionY = y;
                    return false;
                case MotionEvent.ACTION_CANCEL:
                    touchState = TOUCH_STATE_REST;
                    break;
                case MotionEvent.ACTION_UP:
                    touchListener.onTouch(this, ev);
                    return touchState == TOUCH_STATE_SCROLLING_Y;
                default:
                    break;
            }
        }

        return super.onInterceptTouchEvent(ev);
    }

    /**
     * Check if the user is moving the cell
     *
     * @param x Position X
     * @param y Position Y
     */
    private void checkInMoving(float x, float y) {
        final int xDiff = (int) Math.abs(x - lastMotionX);
        final int yDiff = (int) Math.abs(y - lastMotionY);

        final int touchSlop = this.touchSlop;
        boolean xMoved = xDiff > touchSlop;
        boolean yMoved = yDiff > touchSlop;

        if (xMoved) {
            touchState = TOUCH_STATE_SCROLLING_X;
            lastMotionX = x;
            lastMotionY = y;
        }

        if (yMoved) {
            touchState = TOUCH_STATE_SCROLLING_Y;
            lastMotionX = x;
            lastMotionY = y;
        }
    }

    /**
     * Close all opened items
     */
    public void closeOpenedItems() {
        touchListener.closeOpenedItems();
    }

}
