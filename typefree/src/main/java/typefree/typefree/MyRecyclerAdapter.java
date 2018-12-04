package typefree.typefree;

import android.app.AlertDialog;
import android.app.FragmentManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Rect;
import android.media.MediaPlayer;
import android.net.Uri;
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
import android.widget.TextView;
import android.widget.Toast;

import org.w3c.dom.Text;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import typefree.typefree.Base;

import static typefree.typefree.Base.rmodeldir;

public class MyRecyclerAdapter extends RecyclerView.Adapter<MyRecyclerAdapter.ViewHolder> implements Filterable {
    private List<AFile> data_;
    private List<AFile> data_filtered_;
    private LayoutInflater mInflater;
    private Context context;
    private FileRepository f_repo;
    private FragmentManager fragmentManager;
    private Thread t;

    MyRecyclerAdapter(Context ctx, FileRepository f_repo, FragmentManager fragmentManager) {
        this.context = ctx;
        this.mInflater = LayoutInflater.from(ctx);
        this.f_repo = f_repo;
        this.fragmentManager = fragmentManager;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = mInflater.inflate(R.layout.recyclerview_row, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int pos) {
        AFile elem = getItem(pos);
        holder.tv_fname.setText(elem.title);
        holder.tv_date.setText(elem.date);

        String duration = Base.sec_to_timestr(elem.len_s);
        holder.tv_flen.setText(duration);
        holder.curr_pos = pos;

        File txt_file = new File(Base.filesdir + elem.fname + Base.file_suffixes.get("text"));
        if (txt_file.exists()) {
            holder.button_trans.setImageResource(R.drawable.textfile);
        } else {
            holder.button_trans.setImageResource(R.drawable.mic_full);
        }
    }

    @Override
    public int getItemCount() {
        if (data_filtered_ != null) {
            return data_filtered_.size();
        } else {
            return 0;
        }
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        TextView tv_fname, tv_flen, tv_date;
        ImageButton button_opts, button_trans, button_img;
        public View itemView;
        public int curr_pos;

        ViewHolder(View v) {
            super(v);
            tv_fname = v.findViewById(R.id.Row_Filename);
            tv_flen = v.findViewById(R.id.Row_Audiolength);
            tv_date = v.findViewById(R.id.Row_Date);
            button_opts = v.findViewById(R.id.Row_ViewOpts);
            button_trans = v.findViewById(R.id.Row_TransBut);

            button_trans.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {

                    final AFile afile_to_use = data_filtered_.get(curr_pos);
                    String fname = afile_to_use.fname;
                    final String fpath = Base.filesdir + fname;
                    final String wavpath = Base.filesdir + fname + ".wav";
                    Log.i("APP", "Pressed on file " + fpath + " with name " + afile_to_use.title);
                    MediaPlayer mPlayer = MediaPlayer.create(context, Uri.parse(wavpath));
                    int dur = (int) ((float)mPlayer.getDuration() / 4000.0f);
                    String est_time = Base.sec_to_timestr(dur);
                    AlertDialog.Builder builder = new AlertDialog.Builder(context);
                    builder.setTitle("Transcribe audio file")
                            .setMessage("This is estimated to take: " + est_time + "\n\nThis will run in the background so" +
                                    " you can switch to other apps while it runs, but your phone will run slower than normal.")
                            .setPositiveButton("Transcribe", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    final RecEngine recEngine = RecEngine.getInstance(rmodeldir);  // RISKY!! what if it was GCed and needs to be recreated?
                                    t = new Thread(new Runnable() {
                                        @Override
                                        public void run() {
                                            recEngine.transcribe_file(wavpath, fpath);
                                        }
                                    });
                                    t.setPriority(6);
                                    t.start();
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
                            final AFile afile_to_use = data_filtered_.get(curr_pos);
                            switch (item.getItemId()) {
                                case R.id.View:
                                    File f = new File(Base.filesdir + afile_to_use.fname + ".wav");
                                    if (!f.exists()) {
                                        Log.i("APP", "File does not exist, title: " +
                                                afile_to_use.title + " fname: " + afile_to_use.fname + " fpath: " + f.getPath());
                                        Toast.makeText(context, "File does not exist!", Toast.LENGTH_SHORT).show();
                                        break;
                                    }
                                    Intent intent = new Intent(context, FileInfo.class);
                                    intent.putExtra("file_obj", afile_to_use);
                                    context.startActivity(intent);
                                    break;
                                case R.id.Share:
                                    ShareConvDialogFragment dialog = ShareConvDialogFragment.newInstance(afile_to_use.fname);
                                    dialog.show(((AppCompatActivity) context).getSupportFragmentManager(), "ShareDialog");
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
                    final AFile afile_to_use = data_filtered_.get(curr_pos);
                    File f = new File(Base.filesdir + afile_to_use.fname + ".wav");
                    if (!f.exists()) {
                        Log.i("APP", "File does not exist, title: " +
                                afile_to_use.title + " fname: " + afile_to_use.fname + " fpath: " + f.getPath());
                        Toast.makeText(context, "File does not exist!", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Intent intent = new Intent(context, FileInfo.class);
                    intent.putExtra("file_obj", afile_to_use);
                    context.startActivity(intent);
                }
            });
        }

    }

    void delete(AFile afile, int pos) {
        f_repo.delete(afile);
        data_filtered_.remove(pos);
        ArrayList<AFile> all_data = new ArrayList<>();
        for (AFile a_afile : data_) {
            if (!a_afile.fname.equals(afile)) {
                all_data.add(a_afile);
            }
        }
        data_ = all_data;
        Toast.makeText(context, afile.title + " deleted.", Toast.LENGTH_SHORT).show();
    }

    void setData(List<AFile> data) {
        data_ = data;
        data_filtered_ = data;
        notifyDataSetChanged();
    }

    public AFile getItem(int pos) {
        return data_filtered_.get(pos);
    }

    @Override
    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence char_seq) {
                String cString = char_seq.toString();
                if (cString.isEmpty()) {
                    data_filtered_ = data_;
                } else {
                    ArrayList<AFile> filtered_list = new ArrayList<AFile>();
                    for (AFile afile : data_) {
                        if (afile.title.contains(cString)) {
                            filtered_list.add(afile);
                        }
                    }
                    data_filtered_ = filtered_list;
                }

                FilterResults filterResults = new FilterResults();
                filterResults.values = data_filtered_;
                return filterResults;
            }

            @Override
            protected void publishResults(CharSequence char_seq, FilterResults filterResults) {
                data_filtered_ = (ArrayList<AFile>) filterResults.values;
                notifyDataSetChanged();
            }
        };
    }
}
