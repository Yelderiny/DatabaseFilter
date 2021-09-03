
public class Property
{
    //data fields
    private String name; //building || compound name
    private String location; //location in the country
    private String bedrooms; //number of bedrooms in the property
    private int pNum; //the primary key in both lists (property number)
    private double size; //size in sq ft

    //accessors
    public String getName() { return name; }
    public String getLocation() { return location; }
    public String getBedrooms() { return bedrooms; }
    public int getpNum() { return pNum; }
    public double getSize() { return size; }



    //mutators
    public void setName(String name) { this.name = name; }
    public void setLocation(String location) { this.location = location; }
    public void setBedrooms(String roomNum) { this.bedrooms = roomNum; }
    public void setpNum(int pNum) { this.pNum = pNum; }

    public void setSize(double size) { this.size = size; }


    @Override
    public String toString()
    {
        return String.format("%s, %s, %s",getName(), getLocation(), getBedrooms());
    }
}

