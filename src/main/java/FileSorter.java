import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

//TODO create something to hold two owner objects with the same phone number

public class FileSorter
{
    //data fields
    private final ArrayList<Property> availableProperties = new ArrayList<>();
    private final ArrayList<Owner> allOwners = new ArrayList<>(); //clients without properties
    private final ArrayList<Owner> prospectiveClients = new ArrayList<>(); //clients with properties
//    private final ArrayList<Owner> investors = new ArrayList<>();
    private final String[] rejectedOwners = {"bank", "properties", "limited", "investment", "estate", "estates",
            "engineering", "development", "llc", "l.l.c", "(l.l.c)", "ltd.", "finance", "commercial", "co", "h.h.",
            "sheikh", "tamweel", "united", "capital"}; //list of owner keywords not allowed in the prospectiveClients list

    /**
     * Extracts all the information from the input Excel files, transforms them into objects, filters them, then outputs them
     * into an output file
     */
    public void readExcel(String inPath, String outPath )
    {
        File excelFile = new File(inPath); //get input excel file

        try (FileInputStream input = new FileInputStream(excelFile))
        {
            XSSFWorkbook workbook = new XSSFWorkbook(input); //get workbook from the FileInputStream

            XSSFSheet propSheet = workbook.getSheetAt(0); //instantiate sheet 1: Properties
            XSSFSheet ownSheet = workbook.getSheetAt(1); //instantiate sheet 2: Owners

            availableProperties.addAll(rowToProperty(propSheet)); //fill availableProperties with all the properties in sheet 1
            allOwners.addAll(rowToOwner(ownSheet)); //fill allOwners with all the owners in sheet 2

            setProperty(); //prospective clients must be filled at this point


            createFile(outPath);
//            createExcelFile(outPath);
        }
        catch (IOException ioe) { ioe.printStackTrace(); }
    }

    /**
     * Transforms a row into a com.dreamcatcherbroker.leadgenerator.Property Arraylist from an Excel sheet
     * @param propSheet current Excel sheet
     * @return an arraylist of com.dreamcatcherbroker.leadgenerator.Property objects
     */
    private ArrayList<Property> rowToProperty(XSSFSheet propSheet)
    {
        ArrayList<Property> propertyList = new ArrayList<>(); //return variable

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
                    case 0 -> prop.setpNum((int) cell.getNumericCellValue());
                    case 1 -> prop.setLocation(cell.getStringCellValue());
                    case 3 -> prop.setName(cell.getStringCellValue());
                    case 10 -> prop.setBedrooms(cell.getStringCellValue());
                    case 16 -> prop.setSize(cell.getNumericCellValue());
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
    private ArrayList<Owner> rowToOwner(XSSFSheet ownSheet)
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
                    case 0 -> owner.setpNum((int) cell.getNumericCellValue());
                    case 6 -> owner.setName(cell.getStringCellValue());
                    case 13 -> owner.setSex(cell.getStringCellValue());
                    case 10 -> owner.setEmail(cell.getStringCellValue());
                    case 9, 15, 16 -> setPhoneNums(owner, cell);
                }
            }
            if (isQualified(owner)) ownersList.add(owner); //add the owner to the output list if they are qualified
        }
        return ownersList;
    }

    /**
     * Invoked when creating the owner object from the input sheet, this method searches the input cell for a valid
     * phone number, calls the reformat method to make sure the numbers are proper, then adds the number to the owner
     * object if it is unique and valid
     * @param owner current owner
     * @param cell current cell
     */
    private void setPhoneNums(Owner owner, Cell cell)
    {
        boolean unique = true; //to track whether the number is already registered under that owner

        //the cell is not empty and the number is longer than 5 digits
        if (!cell.getStringCellValue().equals("") && cell.getStringCellValue().length() > 5)
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
        StringBuilder reformattedNum = new StringBuilder(" "); //initialize StringBuilder
        char[] num = phoneNumber.toCharArray(); //convert to char array

        //traverse the characters
        for (char c : num)
        {
            if (Character.isDigit(c)) reformattedNum.append(c); //add them to the StringBuilder only if they are numbers
        }
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
            for (Property prop : availableProperties)
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
     * Rules out unqualified owners from the allOwners list so that only secondary market owners make it onto that list
     * @param owner a property owner
     * @return true if the client isn't a developer or a government official
     */
    private boolean isQualified(final Owner owner)
    {
        String[] names = owner.getName().split(" "); //isolate each word in the name

        //check 1: owned by a developer/corporation/sheikh

        //traverse the words in the name
        for (String word : names)
        {
            //traverse the keywords
            for (String keyword : rejectedOwners)
            {
                if (word.equalsIgnoreCase(keyword)) return false; //false if there is a match between the name and the keyword
            }
        }
        //check 2: no phone numbers or emails
        return !owner.getEmail().equals("") || !owner.getPhoneNums().isEmpty();
    }

    /**
     * Creates a CSV file from the prospectiveClients extracted by findClients()
     */
    public void createFile(String outPath)
    {
        Path output = Path.of(outPath);

        try(PrintWriter writer = new PrintWriter(
                new BufferedWriter(
                        new OutputStreamWriter(Files.newOutputStream(output)))))
        {
            ArrayList<Owner> investors = prospectiveClients.stream().filter(client -> client.getProperties().size() > 1).collect(Collectors.toCollection(ArrayList::new));

            investors.forEach(writer::println);
        }
        catch (IOException e) { e.printStackTrace(); }
    }

    /**
     * Creates an Excel file from the prospectiveClients arraylist
     * @param outPath output location
     */
    public void createExcelFile(String outPath)
    {
        File excelFile = new File(outPath); //get input excel file


        try(FileOutputStream output = new FileOutputStream(excelFile))
        {
            XSSFWorkbook workbook = new XSSFWorkbook(); //create blank workbook
            XSSFSheet spreadsheet = workbook.createSheet( " Prospective Clients "); //create first spreadsheet
            XSSFSheet spreadsheet2 = workbook.createSheet(" Investors "); //create second spreadsheet

            Map <String, Object[]> clientInfo = new TreeMap<>(); //maps a number index to client information
            Map <String, Object[]> investorInfo = new TreeMap<>(); //maps a number index to investor information

            clientInfo.put( "1", new Object[] {"Name", "e-mail", "Phone Number(s)", "Property" }); //create the file header row
            investorInfo.put("1", new Object[] {"Name", "e-mail", "Phone Number(s)", "Properties"}); //create the filer header row

            ArrayList<Owner> lonelyClients = prospectiveClients.stream().filter(client -> client.getProperties().size() == 1).collect(Collectors.toCollection(ArrayList::new)); //filter out clients with one property
            ArrayList<Owner> investors = prospectiveClients.stream().filter(client -> client.getProperties().size() > 1).collect(Collectors.toCollection(ArrayList::new)); //filter out clients with multiple properties

            //prepare Excel sheets
            ExcelFileHelper(spreadsheet, clientInfo, lonelyClients);
            ExcelFileHelper(spreadsheet2, investorInfo, investors);

            workbook.write(output); //write the data into the workbook

        }
        catch (Exception e) { e.printStackTrace(); };
    }

    /**
     * Writes into the given map then sets up the Excel sheet
     * @param spreadsheet current spreadsheet
     * @param map current map of indexes and clients
     * @param clients owners of properties
     */
    private void ExcelFileHelper(XSSFSheet spreadsheet, Map<String, Object[]> map, ArrayList<Owner> clients)
    {
        int tracker = 2; //tracker (starts at '2' because at '1' is the row header
        Row row;

        //iterate clients
        for (Owner owner : clients)
        {
            map.put( "" + tracker++, new Object[] {owner.getName(), owner.getEmail(), owner.getPhoneNums().toString(), owner.getProperties().toString()}); //for each client, map an index to the client information
        }

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
        FileSorter kimmy = new FileSorter();
        FileSorter timmy = new FileSorter();


        jimmy.readExcel("/Users/yelderiny/Intelligence/DreamCatcher/Data/Downtown Dubai/Downtown Dubai.xlsx",
                "/Users/yelderiny/Intelligence/DreamCatcher/Database Filter/DD Investors.txt");

        kimmy.readExcel("/Users/yelderiny/Intelligence/DreamCatcher/Data/Business Bay/Business Bay.xlsx",
                "/Users/yelderiny/Intelligence/DreamCatcher/Database Filter/BB Investors.txt");

        timmy.readExcel("/Users/yelderiny/Intelligence/DreamCatcher/Data/JVC/JVC&JVT.xlsx",
                "/Users/yelderiny/Intelligence/DreamCatcher/Database Filter/JV Investors.txt");


    }
}
