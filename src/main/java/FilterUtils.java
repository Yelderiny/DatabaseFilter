import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class FilterUtils
{

    //TODO investigate google phone number library

    /**
     * Changes a name from whatever case it is at input to Camel Casing
     * @param names the name as a String
     * @return the name in camel casing
     */
    public static String reformatName(final String names)
    {
        final String[] fullName = names.split(" ");
        final var finalName = new StringBuilder();

        for (String name : fullName)
        {
            if (!name.isEmpty())
            {
                name = name.charAt(0) + name.substring(1).toLowerCase();
                finalName.append(name).append(" ");
            }
        }
        return finalName.toString();
    }

    /**
     * Takes a phone number as a string and removes any excess characters like '-' or '|' or '+' leaving only the numbers
     * @param phoneNumber a phone number as a string
     * @return a phone number as a String
     */
    public static String reformatNumber(final String phoneNumber)
    {
        final var newNum = new StringBuilder(); //initialize StringBuilder
        final char[] num = phoneNumber.toCharArray(); //convert to char array

        //traverse the characters
        for (final char c : num) if (Character.isDigit(c)) newNum.append(c); //add them to the StringBuilder only if they are numbers

        //reformat numbers from 0XY to 971XY
        if (    newNum.substring(0,3).equals("050") ||
                newNum.substring(0,3).equals("052") ||
                newNum.substring(0,3).equals("055") ||
                newNum.substring(0,3).equals("056") ||
                newNum.substring(0,3).equals("057") ||
                newNum.substring(0,3).equals("058")) newNum.replace(0, 1, "971");

        //reformat numbers from XY to 971XY
        if ((   newNum.substring(0,2).equals("50") ||
                newNum.substring(0,2).equals("52") ||
                newNum.substring(0,2).equals("55") ||
                newNum.substring(0,2).equals("56") ||
                newNum.substring(0,2).equals("57") ||
                newNum.substring(0,2).equals("58"))
                && newNum.length() == 9) newNum.insert(0, "971");

        if (newNum.substring(0,5).equals("97105")) newNum.deleteCharAt(3); //reformat numbers from 9710XY to 971XY

        if (newNum.substring(0,2).equals("00")) newNum.delete(0,2); //reformat numbers from 00... to ...

        return newNum.toString().trim(); //return the string trimmed
    }

    /**
     * Checks if a number is valid based on how a UAE number is supposed to look like
     * @param num phone number
     * @return a bool to represent whether the phone number is valid or not
     */
    public static boolean isValidNumber(final String num)
    {
        boolean a = num.startsWith("9715") &&
                (num.substring(4).equals("000") &&
                        num.substring(4).equals("0000") &&
                        num.substring(4).equals("00000") &&
                        num.substring(4).equals("000000") &&
                        num.substring(4).equals("0000000") &&
                        num.substring(4).equals("00000000"));
        boolean b = num.startsWith("971") && num.length() != 12;
        boolean c = num.startsWith("04");

        return !a && !b && !c;
    }

    /**
     * Checks if an email is valid
     * @param eMail input email as String
     * @return a boolean that represent the email validity
     */
    public static boolean isValidEmail(String eMail)
    {
        //0@0.com, 000@000.ooo, 1@h.com, 1@GMAIL.COM, 0000@0000.com, 00@00.COM, 1@1.COM, 00@000.com, H@HOTMAIL.COM
        if (!eMail.contains("@")) return false;

        final String[] parts = eMail.split("@");
        final String[] second_part = parts[1].split("\\.");

        boolean a = parts[0].length() == 1;
        boolean b = parts[0].contains("dummy");

        boolean c = second_part[0].length() == 1;
        boolean d = isValidEmailHelper(second_part[0]);

        boolean e = isValidEmailFormat(eMail);

        return !a && !b && !c && !d && e;
    }

    /**
     * Checks if the format of the email is correct
     * @param eMail as a string
     * @return true if the format is correct, else false
     */
    private static boolean isValidEmailFormat(String eMail)
    {
        final String ePattern = "^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@((\\[[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\])|(([a-zA-Z\\-0-9]+\\.)+[a-zA-Z]{2,}))$";
        Pattern p = java.util.regex.Pattern.compile(ePattern);
        Matcher m = p.matcher(eMail);
        return m.matches();
    }

    /**
     * Checks if a given string is an integer
     * @param part given string
     * @return true if it is an integer and false if it is not
     */
    private static boolean isValidEmailHelper(String part)
    {
        try { final int intvalue = Integer.parseInt(part);}
        catch (NumberFormatException e) { return false; }
        return true;
    }

    /**
     * Invoked when first creating the prospectiveClients arraylist, this method checks if there is any element of one
     * arraylist that is present in the other arraylist. This is to find out if two arraylists have the
     * same elements
     * @param a an arraylist of phone numbers
     * @param b an arraylist of phone numbers
     * @return true if there is a common phone number in both lists, false otherwise
     */
    public static boolean hasCommonElements(final List<String> a, final List<String> b)
    {
        final List<String> c = a.stream().filter(b::contains).collect(Collectors.toCollection(ArrayList::new));
        return !c.isEmpty();
    }

}
