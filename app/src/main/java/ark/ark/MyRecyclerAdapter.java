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
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import org.w3c.dom.Text;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MyRecyclerAdapter extends RecyclerView.Adapter<MyRecyclerAdapter.ViewHolder> {
    private List<AFile> data_;
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
        if (data_ == null) {
            holder.tv_fname.setText("No files present.");
        }
        AFile elem = getItem(pos);
        holder.tv_fname.setText(elem.title);
        String date = elem.date.substring(4);
        holder.tv_date.setText(date);

        int len_s = elem.len_s;
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
        if (data_ != null) {
            return data_.size();
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
                            final AFile afile_to_use = data_.get(curr_pos);
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
        data_.remove(pos);
        Toast.makeText(context, afile.title + " deleted.", Toast.LENGTH_SHORT).show();
    }

    void setData(List<AFile> data) {
        data_ = data;
        notifyDataSetChanged();
    }

    public AFile getItem(int pos) {
        return data_.get(pos);
    }
}
