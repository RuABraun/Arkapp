package typefree.typefree;


public class DivItem extends ListItem {
    private String datestr;
    public DivItem(String s) {
        datestr = s;
    }
    public int getType() { return TYPE_HEADER; }

    public String getDatestr() {
        return datestr;
    }
}