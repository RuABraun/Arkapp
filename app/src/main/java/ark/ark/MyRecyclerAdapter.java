package ark.ark;

import android.app.AlertDialog;
import android.app.FragmentManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import org.w3c.dom.Text;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MyRecyclerAdapter extends RecyclerView.Adapter<MyRecyclerAdapter.ViewHolder> implements Filterable {
    private List<AFile> data_;
    private List<AFile> data_filtered_;
    private LayoutInflater mInflater;
    private Context context;
    private FileRepository f_repo;
    private FragmentManager fragmentManager;

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

        int len_s = elem.len_s;
        Log.i("APP", "length s " + len_s);
        ArrayList rests = new ArrayList();
        while (len_s % 60 != 0) {
            rests.add(len_s % 60);
            len_s /= 60;
        }
        String[] times = {"h ", "m ", "s"};
        int start_idx = 3 - rests.size();
        String duration = "";
        for (int i = start_idx; i < 3; i++) {
            duration += String.valueOf(rests.get(2-i)) + times[i];  // -i to reverse
        }
        holder.tv_flen.setText(duration);
        holder.curr_pos = pos;

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
        public View itemView;
        public int curr_pos;

        ViewHolder(View v) {
            super(v);
            tv_fname = v.findViewById(R.id.Row_Filename);
            tv_flen = v.findViewById(R.id.Row_Audiolength);
            tv_date = v.findViewById(R.id.Row_Date);

            itemView = v;
            itemView.setOnClickListener(new View.OnClickListener() {
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
