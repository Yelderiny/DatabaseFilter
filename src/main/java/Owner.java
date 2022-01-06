import java.io.Serial;
import java.util.ArrayList;
import java.util.Objects;

public class Owner
{
        //data fields
        private String name; //owner name
        private String email;
        private String sex;
        private int pNum; //the primary key in both lists (property number)

        //list of properties owned
        private final ArrayList<Property> properties = new ArrayList<>()
        {
            @Serial
            private static final long serialVersionUID = 1L;

            @Override
            public String toString() { return super.toString().substring(1,super.toString().length()-1) ; } //this removes the brackets from the arraylist toString() method
        };

        //list of phone numbers provided
        private final ArrayList<String> phoneNums = new ArrayList<>()
        {
            @Serial
            private static final long serialVersionUID = 1L;

            @Override
            public String toString() { return super.toString().substring(1,super.toString().length()-1) ; } //this removes the brackets from the arraylist toString() method
        };

        //accessors
        public String getName() { return name; }
        public String getEmail() { return email; }
        public String getSex() { return sex; }
        public int getpNum() { return pNum; }
        public ArrayList<Property> getProperties() { return properties; }
        public ArrayList<String> getPhoneNums() { return phoneNums; }

        //mutators
        public void setName(String name) { this.name = name; }
        public void setEmail(String email) { this.email = email; }
        public void setSex(String sex) { this.sex = sex; }
        public void setpNum(int pNum) { this.pNum = pNum; }
        public void addProperty(Property property) { properties.add(property); }
        public void addProperty(ArrayList<Property> list)
        {
            for (Property property: list)
            {
                addProperty(property);
            }
        }
        public void addPhoneNums(String phoneNum) { phoneNums.add(phoneNum); }


        @Override
        public String toString()
        {
            var sb = new StringBuilder();
            getProperties().stream().filter(Objects::nonNull).forEach(property -> sb.append("- ").append(property).append("\n"));

            return String.format("Name: %s -%s \ne-Mail: %s \n", getName(), getSex(), getEmail())
                    .concat("Phone Numbers: " + getPhoneNums().toString() + "\nProperties: \n" + sb);
        }

}

