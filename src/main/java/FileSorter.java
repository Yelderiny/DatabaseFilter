import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

//TODO instead of extracting the information based on the column index, create a function that will scan the first row in search of a specified String like 'P-Number'
//TODO understand and use the Google libPhoneNumber library to validate phone numbers

public class FileSorter
{
    //raw data
    private final ArrayList<Property> allProperties = new ArrayList<>(); //all properties
    private final ArrayList<Owner> allOwners = new ArrayList<>(); //clients without properties

    //refined data
    private final ArrayList<Owner> prospectiveClients = new ArrayList<>(); //owners with properties
    private final ArrayList<Owner> investors = new ArrayList<>(); //owners with multiple properties

    //constraints
    private final String[] rejectedOwners = {"bank", "properties", "limited", "investment", "estate", "estates",
            "engineering", "development", "llc", "l.l.c", "(l.l.c)", "ltd.", "ltd",  "finance", "commercial", "co", "h.h.",
            "sheikh", "tamweel", "united", "capital", "company", "aal", "h.h.al", "h.e", "p.j.s.c"}; //list of owner keywords not allowed in the prospectiveClients list

    /**
     * Extracts all the information from the input Excel files, transforms them into objects, filters them, then outputs them
     * into an output file
     */
    public void readExcel(final String inPath, final String outPath )
    {
        File excelFile = new File(inPath); //get input excel file

        try (FileInputStream input = new FileInputStream(excelFile))
        {
            XSSFWorkbook workbook = new XSSFWorkbook(input); //get workbook from the FileInputStream

            XSSFSheet propSheet = workbook.getSheetAt(0); //instantiate sheet 1: Properties
            XSSFSheet ownSheet = workbook.getSheetAt(1); //instantiate sheet 2: Owners

            allProperties.addAll(rowToProperty(propSheet)); //fill availableProperties with all the properties in sheet 1
            allOwners.addAll(rowToOwner(ownSheet)); //fill allOwners with all the owners in sheet 2

            setProperty(); //prospective clients must be filled at this point

//            System.out.println(cellSeeker(propSheet, "P-NUMBER", "AREA", "BUILDING NAME","ROOMS DESCRIPTION", "ACTUAL AREA"));
//            createFile(outPath);
            createExcelFile(outPath);
        }
        catch (IOException ioe) { ioe.printStackTrace(); }
    }

    /**
     * Transforms a row into a Property Arraylist from an Excel sheet
     * @param propSheet current Excel sheet
     * @return an arraylist of Property objects
     */
    private ArrayList<Property> rowToProperty(final XSSFSheet propSheet)
    {
        ArrayList<Property> propertyList = new ArrayList<>(); //return variable
//        ArrayList<Cell> cellNums = new ArrayList<>(cellSeeker(propSheet, "P-NUMBER", "AREA", "BUILDING NAME","ROOMS DESCRIPTION", "ACTUAL AREA"));

        //traverse every Row with enhance for loop
        for (Row row : propSheet)
        {
            if (row.getRowNum() == 0) continue;

            Property prop = new Property(); //create new com.dreamcatcherbroker.leadgenerator.Property instance

            //traverse every cell in the row
            for (Cell cell : row)
            {
                //identify the column and add to the object instance accordingly
                switch (cell.getColumnIndex())
                {
                    case 0 -> prop.setpNum(Integer.parseInt(cell.getStringCellValue()));
                    case 1 -> prop.setLocation(cell.getStringCellValue());
                    case 9 -> prop.setName(cell.getStringCellValue());
                    case 5 -> prop.setBedrooms(cell.getStringCellValue());
                    case 6 -> prop.setSize(cell.getNumericCellValue());
                }
            }
            propertyList.add(prop); //add the property to the output list
        }
        return propertyList;
    }

    /**
     * Transforms a row into an com.dreamcatcherbroker.leadgenerator.Owner Arraylist from an Excel sheet and filters out primary market owners
     * @param ownSheet current Excel sheet
     * @return an arraylist of com.dreamcatcherbroker.leadgenerator.Owner objects
     */
    private ArrayList<Owner> rowToOwner(final XSSFSheet ownSheet)
    {
        ArrayList<Owner> ownersList = new ArrayList<>(); //return variable

        //traverse every Row with enhance for loop
        for (Row row : ownSheet)
        {
            if (row.getRowNum() == 0) continue;

            Owner owner = new Owner(); //create new com.dreamcatcherbroker.leadgenerator.Owner instance

            //traverse every cell in the row
            for(Cell cell : row)
            {
                //identify the column and add to the object instance accordingly
                switch (cell.getColumnIndex())
                {
                    case 0 -> owner.setpNum(Integer.parseInt(cell.getStringCellValue()));
                    case 6 -> owner.setName(reformatName(cell));
                    case 13 -> owner.setSex(cell.getStringCellValue());
//                    case 10 -> owner.setEmail(cell.getStringCellValue());
                    case 9, 15, 16 -> setPhoneNums(owner, cell);
                }
            }
            if (isQualified(owner)) ownersList.add(owner); //add the owner to the output list if they are qualified
        }
        return ownersList;
    }

    private String reformatName(final Cell cell)
    {
        String[] names = cell.getStringCellValue().split(" ");
        StringBuilder finalName = new StringBuilder();

        for (String name : names)
        {
            if (!name.isEmpty())
            {
                name = name.charAt(0) + name.substring(1).toLowerCase();
                finalName.append(name).append(" ");
            }
        }
        return finalName.toString();
    }

    //TODO test this. It's meant to locate a cell in the top row based on the string input
//    private int cellSeeker(final XSSFSheet ownSheet, final String name)
//    {
//        Row row = ownSheet.getRow(0);
//
//        for (Cell cell : row)
//        {
//            if (cell.getStringCellValue().equalsIgnoreCase(name)) return cell.getColumnIndex();
//        }
//        return -1;
//    }

//    private ArrayList<Cell> cellSeeker(final XSSFSheet sheet, String... args)
//    {
//        ArrayList<Cell> cellNums = new ArrayList<>();
//
//        Row row = sheet.getRow(0);
//
//        for (String keyword : args)
//        {
//            boolean found = false;
//            for (Cell cell : row)
//            {
//                if (keyword.equalsIgnoreCase(cell.getStringCellValue()))
//                {
//                    found = true;
//                    cellNums.add(cell);
//                }
//            }
//            if (!found) cellNums.add(null);
//        }
//        return cellNums;
//    }

    /**
     * Invoked when creating the owner object from the input sheet, this method searches the input cell for a valid
     * phone number, calls the reformat method to make sure the numbers are proper, then adds the number to the owner
     * object if it is unique and valid
     * @param owner current owner
     * @param cell current cell
     */
    private void setPhoneNums(final Owner owner, final Cell cell)
    {
        boolean unique = true; //to track whether the number is already registered under that owner

        //the cell is not empty and the number is longer than 7 digits and is not a home/work phone
        if (!cell.getStringCellValue().equals("") && cell.getStringCellValue().length() > 7 && !cell.getStringCellValue().startsWith("04"))
        {
            String reformattedNum = NumberReformater(cell.getStringCellValue()); //format the number correctly

            //traverse the arraylist of phone numbers registered under the owner
            for (String phoneNum : owner.getPhoneNums())
            {
                if (reformattedNum.equals(phoneNum))
                {
                    //the number is found
                    unique = false;
                    break;
                }
            }
            //the number is not found... add it
            if (unique) owner.addPhoneNums(reformattedNum);
        }
    }

    /**
     * Takes a phone number as a string and removes any excess characters like '-' or '|' or '+' leaving only the numbers
     * @param phoneNumber a phone number as a string
     * @return a phone number as a String
     */
    private String NumberReformater(final String phoneNumber)
    {
        StringBuilder reformattedNum = new StringBuilder(); //initialize StringBuilder
        char[] num = phoneNumber.toCharArray(); //convert to char array

        //traverse the characters
        for (char c : num) if (Character.isDigit(c)) reformattedNum.append(c); //add them to the StringBuilder only if they are numbers

        //reformat numbers from 0XY to 971XY
        if (    reformattedNum.substring(0,3).equals("050") ||
                reformattedNum.substring(0,3).equals("052") ||
                reformattedNum.substring(0,3).equals("055") ||
                reformattedNum.substring(0,3).equals("056") ||
                reformattedNum.substring(0,3).equals("057") ||
                reformattedNum.substring(0,3).equals("058")) reformattedNum.replace(0, 1, "971");

        //reformat numbers from XY to 971XY
        if ((   reformattedNum.substring(0,2).equals("50") ||
                reformattedNum.substring(0,2).equals("52") ||
                reformattedNum.substring(0,2).equals("55") ||
                reformattedNum.substring(0,2).equals("56") ||
                reformattedNum.substring(0,2).equals("57") ||
                reformattedNum.substring(0,2).equals("58"))
                && reformattedNum.length() == 9) reformattedNum = new StringBuilder("971" + reformattedNum);

        if (reformattedNum.substring(0,2).equals("00")) reformattedNum.delete(0,2); //reformat numbers from 00... to ...


        return reformattedNum.toString().trim(); //return the string trimmed
    }

    /**
     * Builds the prospectiveClients list by matching the owners with their properties. For owners that have multiple
     * properties, that is reflected in the prospectiveClients List
     *
     * Note: this seems inefficient. I don't like it.
     */
    private void setProperty()
    {
        //traverse the list of owners
        for (Owner owner : allOwners)
        {
            boolean unique = true; //to track whether an owner is already in the prospectiveClients list

            //traverse the list of properties
            for (Property prop : allProperties)
            {
                //find the property that belongs to the current owner
                if (owner.getpNum() == prop.getpNum())
                {
                    //traverse the prospectiveClients list
                    for (Owner prospectiveClient : prospectiveClients)
                    {
                        //the owner is already in the list
                        if (owner.getName().equals(prospectiveClient.getName()))
                        {
                            unique = false; //owner is not unique
                            prospectiveClient.addProperty(prop); //add the property to the existing owner in the prospectiveClient list
                        }

                        //the owner is differently named but has the same phone number
                        else if (hasCommonNums(owner.getPhoneNums(), prospectiveClient.getPhoneNums()))
                        {
                            unique = false; //owner is not unique

                            String oldName = prospectiveClient.getName(); //get the old name
                            String newName = owner.getName(); //get the new name

                            if (!oldName.contains(newName)) prospectiveClient.setName(oldName + " & " + newName); //update name
                            prospectiveClient.addProperty(prop); //add the property
                        }
                    }

                    //the owner is not in the prospectiveClients list
                    if (unique)
                    {
                        owner.addProperty(prop); //add the property to the owner
                        prospectiveClients.add(owner); //add the owner to the prospectiveClients list
                    }
                }
            }
        }
    }

    /**
     * Invoked when first creating the prospectiveClients arraylist, this method checks if there is any element of one
     * arraylist that is present in the other arraylist. This is to find out if two arraylists of phone numbers have the
     * same phone number
     * @param a an arraylist of phone numbers
     * @param b an arraylist of phone numbers
     * @return true if there is a common phone number in both lists, false otherwise
     */
    private boolean hasCommonNums(final ArrayList<String> a, final ArrayList<String> b)
    {
        ArrayList<String> c = a.stream().filter(b::contains).collect(Collectors.toCollection(ArrayList::new));
        return !c.isEmpty();
    }


    /**
     * Rules out unqualified owners from the allOwners list so that only secondary market owners make it onto that list
     *
     * This function needs to use the google phone number checking library
     * @param owner a property owner
     * @return true if the client isn't a developer or a government official
     */
    private boolean isQualified(final Owner owner)
    {
        String[] names = owner.getName().split(" "); //isolate each word in the name

        //check 1: owned by a developer/corporation/sheikh

        //traverse the words in the name and the keywords in rejectedOwners
        for (String word : names)
        {
            for (String keyword : rejectedOwners) if (word.equalsIgnoreCase(keyword)) return false; //false if there is a match between the name and the keyword
        }

        //check 2: no phone numbers or emails
        //!owner.getEmail().equals("") ||
        return (!owner.getPhoneNums().isEmpty() &&
                !owner.getPhoneNums().contains("971500") &&
                !owner.getPhoneNums().contains("9715000")) &&
                !owner.getPhoneNums().contains("971500000") &&
                !owner.getPhoneNums().contains("9715000000") &&
                !owner.getPhoneNums().contains("97150000000") &&
                !owner.getPhoneNums().contains("971500000000");
    }

    /**
     * Creates a CSV file from the prospectiveClients extracted by findClients()
     */
    public void createFile(final String outPath)
    {
        Path output = Path.of(outPath);

        try(PrintWriter writer = new PrintWriter(
                                      new BufferedWriter(
                                            new OutputStreamWriter(Files.newOutputStream(output)))))
        {
//            ArrayList<Owner> investors = prospectiveClients.stream().filter(client -> client.getProperties().size() > 1).collect(Collectors.toCollection(ArrayList::new));

//            investors.forEach(writer::println);

            prospectiveClients.forEach(writer::println);
        }
        catch (IOException e) { e.printStackTrace(); }
    }

    /**
     * Creates an Excel file from the prospectiveClients arraylist
     * @param outPath output location
     */
    public void createExcelFile(final String outPath)
    {
        File excelFile = new File(outPath); //get input excel file


        try(FileOutputStream output = new FileOutputStream(excelFile))
        {
            XSSFWorkbook workbook = new XSSFWorkbook(); //create blank workbook
            XSSFSheet spreadsheet = workbook.createSheet( " Prospective Clients "); //create first spreadsheet
            XSSFSheet spreadsheet2 = workbook.createSheet(" Investors "); //create second spreadsheet

            Map <String, Object[]> clientInfo = new TreeMap<>(); //maps a number index to client information
            Map <String, Object[]> investorInfo = new TreeMap<>(); //maps a number index to investor information

            clientInfo.put( "1", new Object[] {"Name", "Phone Number(s)", "Property" }); //create the file header row
            investorInfo.put("1", new Object[] {"Name", "Phone Number(s)", "Properties"}); //create the filer header row

            ArrayList<Owner> lonelyClients = prospectiveClients.stream().filter(client -> client.getProperties().size() == 1).collect(Collectors.toCollection(ArrayList::new)); //filter out clients with one property
            ArrayList<Owner> investors = prospectiveClients.stream().filter(client -> client.getProperties().size() > 1).collect(Collectors.toCollection(ArrayList::new)); //filter out clients with multiple properties

            //prepare Excel sheets
            ExcelFileHelper(spreadsheet, clientInfo, lonelyClients);
            ExcelFileHelper(spreadsheet2, investorInfo, investors);

            workbook.write(output); //write the data into the workbook

        }
        catch (Exception e) { e.printStackTrace(); }
    }

    /**
     * Writes into the given map then sets up the Excel sheet
     * @param spreadsheet current spreadsheet
     * @param map current map of indexes and clients
     * @param clients owners of properties
     */
    private void ExcelFileHelper(final XSSFSheet spreadsheet, final Map<String, Object[]> map, final ArrayList<Owner> clients)
    {
        int tracker = 2; //tracker (starts at '2' because at '1' is the row header
        Row row;

        //iterate clients
        for (Owner owner : clients) map.put( "" + tracker++, new Object[] {owner.getName(), owner.getPhoneNums().toString(), owner.getProperties().toString()}); //for each client, map an index to the client information


        //Iterate over data and write to sheet
        Set <String> keyid = map.keySet(); //extract the indexes of the map
        int rowid = 0; //tracker for the rows

        //iterate the indexes of the map
        for (String key : keyid)
        {
            row = spreadsheet.createRow(rowid++); //create a row in the spreadsheet
            Object[] objectArr = map.get(key); //extract the client object list from the map
            int cellid = 0;

            //iterate the client object list
            for (Object obj : objectArr)
            {
                Cell cell = row.createCell(cellid++); //create a cell for each object in the client object
                cell.setCellValue((String)obj); //write into the cell with the object data
            }
        }
    }


    public static void main(String[] args)
    {
        FileSorter jimmy = new FileSorter();
//        FileSorter kimmy = new FileSorter();
//        FileSorter timmy = new FileSorter();




        jimmy.readExcel("/Users/yelderiny/Intelligence/DreamCatcher/Data/Damac Hills/DAMAC Hills (2020) copy.xlsx",
                "/Users/yelderiny/Intelligence/DreamCatcher/Database Filter/DAMAC Hills Owners.xlsx");

//        kimmy.readExcel("/Users/yelderiny/Intelligence/DreamCatcher/Data/Business Bay/Business Bay.xlsx",
//                "/Users/yelderiny/Intelligence/DreamCatcher/Database Filter/BB Investors.txt");
//
//        timmy.readExcel("/Users/yelderiny/Intelligence/DreamCatcher/Data/JVC/JVC&JVT.xlsx",
//                "/Users/yelderiny/Intelligence/DreamCatcher/Database Filter/JV Investors.txt");


    }
}
