package fragment.sermon;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.database.Cursor;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.os.StrictMode;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.birbit.android.jobqueue.JobManager;
import com.japhethwaswa.church.R;
import com.japhethwaswa.church.databinding.FragmentSermonsAllBinding;
import com.willowtreeapps.spruce.Spruce;
import com.willowtreeapps.spruce.animation.DefaultAnimations;
import com.willowtreeapps.spruce.sort.DefaultSort;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import adapters.recyclerview.sermon.SermonRecyclerViewAdapter;
import db.ChurchContract;
import db.ChurchQueryHandler;
import event.ClickListener;
import event.CustomRecyclerTouchListener;
import event.pojo.SermonDataRetrievedSaved;
import model.dyno.FragDyno;

public class SermonAllFragment extends Fragment {

    private FragmentSermonsAllBinding fragmentSermonsAllBinding;
    //public NavActivity navActivity;
    private JobManager jobManager;
    private FragmentManager localFragmentManager;
    //private FragmentManager localFragmentManager;
    private FragmentTransaction fragmentTransaction;
    private SermonRecyclerViewAdapter sermonRecyclerViewAdapter;
    private Cursor localCursor;
    private int sermonPosition = -1;
    private int orientationChange = -1;
    private int currVisiblePosition = -1;
    private int previousPosition = -1;
    private int dualPane = -1;
    private Animator spruceAnimator;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        //StrictMode
        StrictMode.VmPolicy vmPolicy = new StrictMode.VmPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build();
        StrictMode.setVmPolicy(vmPolicy);
        /**==============**/

        //inflate the view
        fragmentSermonsAllBinding = DataBindingUtil.inflate(inflater, R.layout.fragment_sermons_all, container, false);

        //handle orientation change since it returns to bible books
        Bundle bundle = getArguments();
        orientationChange = bundle.getInt("orientationChange");
        currVisiblePosition = bundle.getInt("positionCurrentlyVisible");
        dualPane = bundle.getInt("dualPane");

        //fragment management

        //navActivity = (NavActivity) getActivity();
        //localFragmentManager = navActivity.fragmentManager;
        localFragmentManager = getActivity().getSupportFragmentManager();

        //set cursor to null
        localCursor = null;


        /**sermon recycler view adapter**/
        sermonRecyclerViewAdapter = new SermonRecyclerViewAdapter(localCursor);

        LinearLayoutManager linearLayoutManagerRecycler = new LinearLayoutManager(getContext()) {
            @Override
            public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
                super.onLayoutChildren(recycler, state);
                //Animate in the visible children
                spruceAnimator = new Spruce.SpruceBuilder(fragmentSermonsAllBinding.sermonsRecycler)
                        .sortWith(new DefaultSort(100))
                        .animateWith(DefaultAnimations.shrinkAnimator(fragmentSermonsAllBinding.sermonsRecycler, 800),
                                ObjectAnimator.ofFloat(fragmentSermonsAllBinding.sermonsRecycler,
                                        "translationX", -fragmentSermonsAllBinding.sermonsRecycler.getWidth(), 0f)
                                        .setDuration(800)).start();
            }
        };

        fragmentSermonsAllBinding.sermonsRecycler.setAdapter(sermonRecyclerViewAdapter);
        fragmentSermonsAllBinding.sermonsRecycler.setLayoutManager(linearLayoutManagerRecycler);

        /**SnapHelper helper = new LinearSnapHelper();
         helper.attachToRecyclerView(fragmentSermonsAllBinding.sermonsRecycler);**/

        /****/
        //add touch listener to recyclerview
        fragmentSermonsAllBinding.sermonsRecycler.addOnItemTouchListener(new CustomRecyclerTouchListener(
                getActivity(), fragmentSermonsAllBinding.sermonsRecycler, new ClickListener() {
            @Override
            public void onClick(View view, int position) {

                //start a new fragment showing specific sermon
                showSpecificSermon(position);

            }

            @Override
            public void onLongClick(View view, int position) {

            }
        }
        ));


        return fragmentSermonsAllBinding.getRoot();
    }

    //fetch all sermons from db.
    private void getSermonFromDb() {
        if (localCursor != null) {
            localCursor.close();
            localCursor = null;
        }
        //show loader
        fragmentSermonsAllBinding.pageloader.startProgress();

        ChurchQueryHandler handler = new ChurchQueryHandler(getContext().getContentResolver()) {
            @Override
            protected void onQueryComplete(int token, Object cookie, Cursor cursor) {

                localCursor = cursor;
                previousPosition = FragDyno.getPrevPosition(getString(R.string.preference_sermon_position));

                if (previousPosition != -1 && orientationChange != -1) {
                    sermonPosition = previousPosition;
                    if (dualPane != -1)
                        loadSermonListToRecyclerView();
                    showSpecificSermon(sermonPosition);
                } else {
                    loadSermonListToRecyclerView();

                }


            }
        };

        String[] projection = {
                ChurchContract.SermonEntry.COLUMN_SERMON_ID,
                ChurchContract.SermonEntry.COLUMN_SERMON_TITLE,
                ChurchContract.SermonEntry.COLUMN_SERMON_IMAGE_URL,
                ChurchContract.SermonEntry.COLUMN_SERMON_BRIEF_DESCRIPTION,
                ChurchContract.SermonEntry.COLUMN_SERMON_AUDIO_URL,
                ChurchContract.SermonEntry.COLUMN_SERMON_VIDEO_URL,
                ChurchContract.SermonEntry.COLUMN_SERMON_PDF_URL,
                ChurchContract.SermonEntry.COLUMN_SERMON_DATE,
                ChurchContract.SermonEntry.COLUMN_SERMON_VISIBLE,
                ChurchContract.SermonEntry.COLUMN_SERMON_CREATED_AT,
                ChurchContract.SermonEntry.COLUMN_SERMON_UPDATED_AT
        };

        String orderBy = ChurchContract.SermonEntry.COLUMN_SERMON_DATE + " DESC";
        //String orderBy = null;

        handler.startQuery(23, null, ChurchContract.SermonEntry.CONTENT_URI, projection, null, null, orderBy);
    }

    private void loadSermonListToRecyclerView() {
        if (localCursor.getCount() > 0) {
            //hide loader here
            fragmentSermonsAllBinding.pageloader.stopProgress();
            //set recycler cursor
            sermonRecyclerViewAdapter.setCursor(localCursor);

            //scroll to position if set
            if (currVisiblePosition != -1) {
                fragmentSermonsAllBinding.sermonsRecycler.scrollToPosition(currVisiblePosition);
            }

        }
    }


    private void showSpecificSermon(int position) {

        //reset the fragment transaction
        fragmentTransaction = localFragmentManager.beginTransaction();

        //save the current position in preferences
        FragDyno.saveToPreference(getString(R.string.preference_sermon_position), position);

        if (localCursor != null) {
            if (localCursor.moveToPosition(position)) {


                SermonSpecific sermonSpecific = new SermonSpecific();
                String sermonId = localCursor.getString(localCursor.getColumnIndex(ChurchContract.SermonEntry.COLUMN_SERMON_ID));

                Bundle bundle = new Bundle();
                bundle.putInt("orientationChange", orientationChange);
                bundle.putInt("sermonId", Integer.valueOf(sermonId));

                sermonSpecific.setArguments(bundle);

                if (dualPane == -1) {
                    fragmentTransaction.replace(R.id.mainSermonFragment, sermonSpecific, "sermonSpecificFragment");
                } else {
                    fragmentTransaction.replace(R.id.mainSermonSpecific, sermonSpecific, "sermonSpecificFragment");
                }


                fragmentTransaction.commit();
            }

        }

    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onSermonDataRetrieved(SermonDataRetrievedSaved event) {
        //parse the sermon item and display
        getSermonFromDb();
    }


    @Override
    public void onPause() {
        super.onPause();
        /**close cursors**/
        if (localCursor != null && localCursor.isClosed() == false) {
            localCursor.close();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        //confirm if is not screen orientation change then clear all the bible,chapters,verse logs in preference file
        if (orientationChange == -1) {
            //clear all preference bible related data
            String[] itemNames = {getString(R.string.preference_sermon_position)};
            FragDyno.clearDataPreference(itemNames);
        }

        getSermonFromDb();

        //register event
        EventBus.getDefault().register(this);
    }

    @Override
    public void onStop() {
        //unregister event
        EventBus.getDefault().unregister(this);
        super.onStop();
    }
}
