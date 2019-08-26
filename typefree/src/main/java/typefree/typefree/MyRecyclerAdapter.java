package typefree.typefree;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.FragmentManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Rect;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.w3c.dom.Text;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import typefree.typefree.Base;

import static typefree.typefree.Base.rmodeldir;



public class MyRecyclerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private volatile List<AFile> data_;
    private volatile List<ListItem> data_grouped_ = new ArrayList<>();
    private LayoutInflater mInflater;
    private MainActivity context;
    private FileRepository f_repo;
    private FragmentManager fragmentManager;
    private Thread t;
    private boolean recog_done = false;
    private Runnable runnable;

    MyRecyclerAdapter(Context ctx, FileRepository f_repo, FragmentManager fragmentManager) {
        this.context = (MainActivity) ctx;
        this.mInflater = LayoutInflater.from(ctx);
        this.f_repo = f_repo;
        this.fragmentManager = fragmentManager;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        switch (viewType) {
            case ListItem.TYPE_AFILE: {
                View view = mInflater.inflate(R.layout.recyclerview_row, parent, false);
                return new AFileViewHolder(view);
            }
            case ListItem.TYPE_HEADER: {
                View view = mInflater.inflate(R.layout.divider, parent, false);
                return new DivViewHolder(view);
            }
            default:
                throw new IllegalStateException("unsupported item type");
        }
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder viewHolder, int pos) {
        pos = viewHolder.getAdapterPosition();
        int itemType = getItemType(pos);
        switch (itemType ) {
            case ListItem.TYPE_AFILE: {
                AFile elem = (AFile) getItem(pos);
                AFileViewHolder holder = (AFileViewHolder) viewHolder;
                holder.tv_fname.setText(elem.title);
                holder.tv_date.setText(elem.date + " \u2022 ");
                String duration = Base.sec_to_timestr(elem.len_s);
                holder.tv_flen.setText(duration);
                holder.curr_pos = pos;
                holder.spinner.setVisibility(View.INVISIBLE);

                File txt_file = new File(Base.filesdir + elem.fname + Base.file_suffixes.get("text"));
                if (txt_file.exists()) {
                    holder.button_trans.setImageResource(R.drawable.textfile);
                } else {
                    holder.button_trans.setImageResource(R.drawable.mic_full);
                }
                break;
            }
            case ListItem.TYPE_HEADER: {
                DivItem div = (DivItem) getItem(pos);
                DivViewHolder holder = (DivViewHolder) viewHolder;
                holder.tv_div.setText(div.getDatestr());
                break;
            }
            default:
                throw new IllegalStateException("unsupported item type");
        }
    }

    public ListItem getItem(int pos) {
        return data_grouped_.get(pos);
    }

    @Override
    public int getItemCount() {
        if (data_ != null) {
            return data_grouped_.size();
        } else {
            return 0;
        }
    }

    public int getItemType(int position) {
        return data_grouped_.get(position).getType();
    }

    @Override
    public int getItemViewType(int position) {
        return data_grouped_.get(position).getType();
    }

    public class DivViewHolder extends RecyclerView.ViewHolder {
        TextView tv_div;
        DivViewHolder(View v) {
            super(v);
            tv_div = v.findViewById(R.id.div_date);
        }
    }

    public class AFileViewHolder extends RecyclerView.ViewHolder {
        TextView tv_fname, tv_flen, tv_date;
        ImageButton button_opts, button_trans;
        final ProgressBar spinner;
        public View itemView;
        public int curr_pos;

        AFileViewHolder(View v) {
            super(v);
            tv_fname = v.findViewById(R.id.Row_Filename);
            tv_flen = v.findViewById(R.id.Row_Audiolength);
            tv_date = v.findViewById(R.id.Row_Date);
            button_opts = v.findViewById(R.id.Row_ViewOpts);
            button_trans = v.findViewById(R.id.Row_TransBut);
            spinner = v.findViewById(R.id.progressBar_trans);

            button_trans.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {

                    final AFile afile_to_use = (AFile) data_grouped_.get(curr_pos);
                    String fname = afile_to_use.fname;
                    final String fpath = Base.filesdir + fname;
                    final String wavpath = Base.filesdir + fname + ".wav";
                    Log.i("APP", "Pressed on file " + fpath + " with name " + afile_to_use.title);
                    MediaPlayer mPlayer = MediaPlayer.create(context, Uri.parse(wavpath));
                    int dur = (int) ((float)mPlayer.getDuration() / 2000.0f);
                    String est_time = Base.sec_to_timestr(dur);
                    AlertDialog.Builder builder = new AlertDialog.Builder(context);

                    builder.setTitle("Transcribe audio file")
                            .setMessage("This is estimated to take: " + est_time + "\n\nThis will run in the background so" +
                                    " probably you can switch to other apps while it runs, but your phone will run slower than normal (and if it does not work try not switching to other apps).")
                            .setPositiveButton("Transcribe", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    final RecEngine recEngine = RecEngine.getInstance(rmodeldir, context.exclusiveCores);  // RISKY!! what if it was GCed and needs to be recreated?
                                    spinner.setVisibility(View.VISIBLE);
                                    button_trans.setVisibility(View.INVISIBLE);
                                    recog_done = false;
                                    context.h_background.postDelayed(new Runnable() {
                                        @Override
                                        public void run() {
                                            Base.temper_performance(context, 60, 30, 15);
                                        }
                                    }, 500);
                                    t = new Thread(new Runnable() {
                                        @Override
                                        public void run() {
                                            recEngine.transcribe_file(wavpath, fpath);
                                            recog_done = true;
                                        }
                                    });
                                    t.setPriority(9);
                                    t.start();
                                    context.h_main.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            runnable = this;
                                            if (recog_done) {
                                                spinner.setVisibility(View.INVISIBLE);
                                                button_trans.setImageResource(R.drawable.textfile);
                                                button_trans.setVisibility(View.VISIBLE);
                                            } else {
                                                context.h_main.postDelayed(runnable, 100);
                                            }
                                        }
                                    });
                                    dialog.dismiss();
                                }
                            }).setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                }
                    });
                    AlertDialog alert = builder.create();
                    alert.show();
                    TextView tv = (TextView) alert.findViewById(android.R.id.message);
                    tv.setTextSize(18);
                }
            });

            button_opts.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    PopupMenu popup = new PopupMenu(context, v);
                    popup.inflate(R.menu.menu_file);
                    popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                        @Override
                        public boolean onMenuItemClick(MenuItem item) {
                            final AFile afile_to_use = (AFile) data_grouped_.get(curr_pos);
                            switch (item.getItemId()) {
                                case R.id.View:
                                    File f = new File(Base.filesdir + afile_to_use.fname + ".wav");
                                    if (!f.exists()) {
                                        Log.i("APP", "File does not exist, title: " +
                                                afile_to_use.title + " fname: " + afile_to_use.fname + " fpath: " + f.getPath());
                                        Toast.makeText(context, "File does not exist! Maybe it still needs to be transcribed (mic button). Contact support for help.", Toast.LENGTH_SHORT).show();
                                        break;
                                    }
                                    Bundle bundle = new Bundle();
                                    bundle.putParcelable("file_obj", afile_to_use);
                                    FileInfo finfo_frag = new FileInfo();
                                    finfo_frag.setArguments(bundle);
                                    fragmentManager.beginTransaction().replace(R.id.main_root_view,
                                            finfo_frag, "fileinfo").addToBackStack(null).commit();
                                    break;
                                case R.id.Share:
                                    ShareConvDialogFragment dialog = ShareConvDialogFragment.newInstance(afile_to_use.fname);
                                    dialog.show(((MainActivity) context).getFragmentManager(), "ShareDialog");
                                    break;

                                case R.id.Delete:
                                    AlertDialog.Builder builder = new AlertDialog.Builder(context);
                                    builder.setTitle("Confirm")
                                            .setMessage("Are you sure?")
                                            .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                                                public void onClick(DialogInterface dialog, int which) {
                                                    delete(afile_to_use, curr_pos);
                                                    dialog.dismiss();
                                                }
                                            }).setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            dialog.dismiss();
                                        }
                                    });
                                    AlertDialog alert = builder.create();
                                    alert.show();
                                    TextView tv = (TextView) alert.findViewById(android.R.id.message);
                                    tv.setTextSize(18);
                                    break;

                            }
                            return false;
                        }
                    });
                    popup.show();

                }
            });

            itemView = v;
            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {

                    final AFile afile_to_use = (AFile) data_grouped_.get(curr_pos);
                    File f = new File(Base.filesdir + afile_to_use.fname + ".wav");
                    if (!f.exists()) {
                        Log.i("APP", "File does not exist, title: " +
                                afile_to_use.title + " fname: " + afile_to_use.fname + " fpath: " + f.getPath());
                        Toast.makeText(context, "File does not exist! Maybe it still needs to be transcribed (mic button). Contact support for help.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Bundle bundle = new Bundle();
                    bundle.putParcelable("file_obj", afile_to_use);
                    FileInfo finfo_frag = new FileInfo();
                    finfo_frag.setArguments(bundle);
                    fragmentManager.beginTransaction().replace(R.id.main_root_view,
                        finfo_frag, "fileinfo").addToBackStack(null).commit();
                }
            });
        }

    }

    void delete(AFile afile, int idx) {
        f_repo.delete(afile);
        data_.remove(idx);
        setData(data_);
        Log.i("APP", "delete: data has size " + data_.size());
        notifyDataSetChanged();
        Toast.makeText(context, afile.title + " deleted.", Toast.LENGTH_SHORT).show();
    }


    void setData(final List<AFile> data) {
        if (data != null) {
            Log.i("APP", "data has size " + data.size());
        }

        Collections.sort(data, new Comparator<AFile>() {
            @Override
            public int compare(AFile lhs, AFile rhs) {
                SimpleDateFormat sdf = new SimpleDateFormat("EEE dd/MM/yyyy HH:mm", Locale.getDefault());
                Date left_date = null;
                try {
                    left_date = sdf.parse(lhs.date);
                } catch (ParseException e) {
                    e.printStackTrace();
                }
                Date right_date = null;
                try {
                    right_date = sdf.parse(rhs.date);
                } catch (ParseException e) {
                    e.printStackTrace();
                }
                return right_date.compareTo(left_date);
            }
        });
        data_ = data;

        SimpleDateFormat sdf = new SimpleDateFormat("EEE dd/MM/yyyy HH:mm", Locale.getDefault());
        if (data_ == null) {
            data_grouped_ = null;
            return;
        }
        Date lastdate = null;
        ArrayList<ListItem> data_buffer = new ArrayList<>();
        for (int i = 0; i < data_.size(); i++) {
            AFile afile = data_.get(i);
            data_buffer.add(afile);
        }
//        for (int i = 0; i < data_.size(); i++) {
//            Date date = null;
//            AFile afile = data_.get(i);
//            try {
//                date = sdf.parse(afile.date);
//            } catch (ParseException e) {
//                e.printStackTrace();
//            }
//
//            if (i > 0) {
//                Calendar c = Calendar.getInstance();
//                c.setTime(date);
//                int n = c.get(Calendar.WEEK_OF_YEAR);
//                Calendar clast = Calendar.getInstance();
//                clast.setTime(lastdate);
//                int lastn = clast.get(Calendar.WEEK_OF_YEAR);
//
//                if (n != lastn) {
//                    Calendar mondayDate = Calendar.getInstance();  // not yet on Monday..
//                    mondayDate.setTime(lastdate);
//                    while (mondayDate.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
//                        mondayDate.add(Calendar.DATE, -1);
//                    }
//                    Date divdate = mondayDate.getTime();
//                    SimpleDateFormat sdf_ = new SimpleDateFormat("EEE dd/MM/yyyy");
//                    String str_divdate = sdf_.format(divdate);
//                    DivItem div = new DivItem(str_divdate);
//                    data_buffer.add(div);
//
//                    if (n - 2 >= lastn) {
//                        Calendar mondayDate2 = Calendar.getInstance();  // not yet on Monday..
//                        mondayDate2.setTime(date);
//                        while (mondayDate2.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
//                            mondayDate2.add(Calendar.DATE, 1);
//                        }
//                        Date divdate2 = mondayDate2.getTime();
//                        String str_divdate2 = sdf_.format(divdate2);
//                        DivItem div2 = new DivItem(str_divdate2);
//                        data_buffer.add(div2);
//                    }
//                }
//            }
//            data_buffer.add(afile);
//            lastdate = date;
//        }
        data_grouped_ = data_buffer;

        notifyDataSetChanged();

    }

//    @Override
//    public Filter getFilter() {
//        return new Filter() {
//            @Override
//            protected FilterResults performFiltering(CharSequence char_seq) {
//                String cString = char_seq.toString();
//                if (cString.isEmpty()) {
//                    data_filtered_ = data_;
//                } else {
//                    ArrayList<AFile> filtered_list = new ArrayList<AFile>();
//                    for (AFile afile : data_) {
//                        if (afile.title.contains(cString)) {
//                            filtered_list.add(afile);
//                        }
//                    }
//                    data_filtered_ = filtered_list;
//                }
//
//                FilterResults filterResults = new FilterResults();
//                filterResults.values = data_filtered_;
//                return filterResults;
//            }
//
//            @Override
//            protected void publishResults(CharSequence char_seq, FilterResults filterResults) {
//                data_filtered_ = (ArrayList<AFile>) filterResults.values;
//                notifyDataSetChanged();
//            }
//        };
//    }
}
