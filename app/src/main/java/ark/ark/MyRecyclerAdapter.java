package ark.ark;

import android.app.FragmentManager;
import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

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
        holder.tv_fname.setText(elem.fname);
        holder.tv_date.setText(elem.date);

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
                    Toast.makeText(context, String.valueOf(curr_pos), Toast.LENGTH_SHORT).show();

                    PopupMenu popup = new PopupMenu(context, v);

                    popup.inflate(R.menu.menu_file);

                    popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                        @Override
                        public boolean onMenuItemClick(MenuItem item) {
                            AFile afile_to_use = data_.get(curr_pos);
                            switch (item.getItemId()) {
                                case R.id.View:
                                    FileFragment fileview = new FileFragment();
                                    fileview.setFileFields(afile_to_use);
                                    fileview.show(fragmentManager, "fileview");
                                    break;
                                case R.id.Export:
                                    break;
                                case R.id.Delete:
                                    f_repo.delete(afile_to_use);
                                    data_.remove(curr_pos);
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

    void setData(List<AFile> data) {
        data_ = data;
        notifyDataSetChanged();
    }

    public AFile getItem(int pos) {
        return data_.get(pos);
    }
}
