import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
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
    private final ArrayList<Property> allProperties = new ArrayList<>(); //all properties
    private final ArrayList<Owner> allOwners = new ArrayList<>(); //owners without properties
    private final ArrayList<Owner> prospectiveClients = new ArrayList<>(); //owners with properties

    //constraints
    private final String[] rejectedOwners = {"bank", "properties", "limited", "investment", "estate", "estates",
            "engineering", "development", "llc", "l.l.c", "(l.l.c)", "ltd.", "ltd",  "finance", "commercial", "co", "h.h.",
            "sheikh", "prince", "princess", "tamweel", "united", "capital", "company", "aal", "h.h.al", "h.e", "p.j.s.c"}; //list of owner keywords not allowed in the prospectiveClients list

    /**
     * Extracts all the information from the input Excel file, transforms them into objects, and filters them.
     * @param inFile the Excel file
     * @param index index of the relevant sheet
     * @param type an 'o' or 'p' which indicates whether it's a file of owners or properties
     */
    public void readExcel(final File inFile, final int index, final String type)
    {
        try (FileInputStream input = new FileInputStream(inFile))
        {
            var workbook = new XSSFWorkbook(input); //get workbook from the FileInputStream

            var sheet = workbook.getSheetAt(index);

            if (type.equalsIgnoreCase("o")) allOwners.addAll(rowToOwner(sheet));
            else if (type.equalsIgnoreCase("p")) allProperties.addAll(rowToProperty(sheet));

            if (!allOwners.isEmpty() && !allProperties.isEmpty()) setProperty();
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
        var propertyList = new ArrayList<Property>(); //return variable
        var headerIndices = new HashMap<>(cellSeeker(propSheet, "P-NUMBER", "AREA", "PROJECT", "ROOMS DESCRIPTION", "ACTUAL AREA"));

        //traverse every Row with enhance for loop
        for (Row row : propSheet)
        {
            if (row.getRowNum() == 0) continue;

            var prop = new Property(); //create new com.dreamcatcherbroker.leadgenerator.Property instance

            //traverse every cell in the row
            for (Cell cell : row)
            {
                //identify the column and add to the object instance accordingly
                if (cell.getColumnIndex() == headerIndices.get("P-NUMBER"))
                {
                    var cellType = cell.getCellType();

                    if (cellType == CellType.NUMERIC) prop.setpNum((int) cell.getNumericCellValue());
                    else if (cellType == CellType.STRING) prop.setpNum(Integer.parseInt(cell.getStringCellValue()));
                }
                else if (cell.getColumnIndex() == headerIndices.get("AREA") && headerIndices.get("AREA") != -1) prop.setLocation(cell.getStringCellValue());
                else if (cell.getColumnIndex() == headerIndices.get("PROJECT") && headerIndices.get("PROJECT") != -1) prop.setName(cell.getStringCellValue());
                else if (cell.getColumnIndex() == headerIndices.get("ROOMS DESCRIPTION") && headerIndices.get("ROOMS DESCRIPTION") != 1) prop.setBedrooms(cell.getStringCellValue());
                else if (cell.getColumnIndex() == headerIndices.get("ACTUAL AREA") && headerIndices.get("ACTUAL AREA") != -1) prop.setSize(cell.getNumericCellValue());
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
        var ownersList = new ArrayList<Owner>(); //return variable
        var headerIndices = new HashMap<>(cellSeeker(ownSheet, "P-NUMBER", "NAME", "GENDER", "PHONE", "MOBILE", "SECONDARY MOBILE"));

        //traverse every Row with enhance for loop
        for (Row row : ownSheet)
        {
            if (row.getRowNum() == 0) continue;

            var owner = new Owner(); //create new com.dreamcatcherbroker.leadgenerator.Owner instance

            //traverse every cell in the row
            for(Cell cell : row)
            {
                //identify the column and add to the object instance accordingly
                if (cell.getColumnIndex() == headerIndices.get("P-NUMBER"))
                {
                    var cellType = cell.getCellType();

                    if (cellType == CellType.NUMERIC) owner.setpNum((int) cell.getNumericCellValue());
                    else if (cellType == CellType.STRING) owner.setpNum(Integer.parseInt(cell.getStringCellValue()));
                }
                else if (cell.getColumnIndex() == headerIndices.get("NAME") && headerIndices.get("NAME") != -1) owner.setName(reformatName(cell));
                else if (cell.getColumnIndex() == headerIndices.get("GENDER") && headerIndices.get("GENDER") != -1) owner.setSex(cell.getStringCellValue());
                else if ((cell.getColumnIndex() == headerIndices.get("PHONE") && headerIndices.get("PHONE") != -1) ||
                        (cell.getColumnIndex() == headerIndices.get("MOBILE") && headerIndices.get("MOBILE") != -1) ||
                        (cell.getColumnIndex() == headerIndices.get("SECONDARY MOBILE") && headerIndices.get("SECONDARY MOBILE") != -1)) setPhoneNums(owner, cell);
            }
            if (owner.getName() != null && isQualified(owner)) ownersList.add(owner); //add the owner to the output list if they are qualified
        }
        return ownersList;
    }

    /**
     * Changes a name from whatever case it is at input to Camel Casing
     * @param cell the cell with the name
     * @return the name in camel casing
     */
    private String reformatName(final Cell cell)
    {
        String[] names = cell.getStringCellValue().split(" ");
        var finalName = new StringBuilder();

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

    /**
     * Attemps to put all arguments in a hashmap with its associated column index in the excel file sheet
     * @param sheet the sheet in question
     * @param headers the input string headers
     * @return a hashmap of headers and their indices
     */
    private HashMap<String, Integer> cellSeeker(final XSSFSheet sheet, final String... headers)
    {
        var indices = new HashMap<String, Integer>();

        for (String keyword : headers)
        {
            boolean found = false;
            for (Cell cell : sheet.getRow(0))
            {
                if (keyword.equalsIgnoreCase(cell.getStringCellValue()))
                {
                    found = true;
                    indices.put(keyword, cell.getColumnIndex());
                }
            }
            if (!found) indices.put(keyword, -1);
        }
        return indices;
    }

    /**
     * Invoked when creating the owner object from the input sheet, this method searches the input cell for a valid
     * phone number, calls the reformat method to make sure the numbers are proper, then adds the number to the owner
     * object if it is unique and valid
     * @param owner current owner
     * @param cell current cell
     */
    private void setPhoneNums(final Owner owner, final Cell cell)
    {
        //the cell is not empty and the number is longer than 7 digits and is not a home/work phone
        if (!cell.getStringCellValue().equals("") && cell.getStringCellValue().length() > 6)
        {
            String num = NumberReformater(cell.getStringCellValue()); //format the number correctly
            boolean valid = isValidNumber(num);

            if (valid && !owner.getPhoneNums().contains(num)) owner.addPhoneNums(num);
        }
    }

    /**
     * Checks if a number is valid based on how a UAE number is supposed to look like
     * @param num phone number
     * @return a bool to represent whether the phone number is valid or not
     */
    private boolean isValidNumber(final String num)
    {
        if (num.startsWith("971") && num.length() != 12) return false;
        if (num.length() < 7) return false;
        if (num.startsWith("04")) return false;
        if (num.startsWith("9715") && (num.substring(4).equals(
                "000") &&
                num.substring(4).equals("0000") &&
                num.substring(4).equals("00000") &&
                num.substring(4).equals("000000") &&
                num.substring(4).equals("0000000") &&
                num.substring(4).equals("00000000"))) return false;

        return true;
    }

    /**
     * Takes a phone number as a string and removes any excess characters like '-' or '|' or '+' leaving only the numbers
     * @param phoneNumber a phone number as a string
     * @return a phone number as a String
     */
    private String NumberReformater(final String phoneNumber)
    {
        var newNum = new StringBuilder(); //initialize StringBuilder
        char[] num = phoneNumber.toCharArray(); //convert to char array

        //traverse the characters
        for (char c : num) if (Character.isDigit(c)) newNum.append(c); //add them to the StringBuilder only if they are numbers

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
        return !owner.getPhoneNums().isEmpty();
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
     * @param outFile output location
     */
    public void createExcelFile(final File outFile)
    {
        try(FileOutputStream output = new FileOutputStream(outFile))
        {
            var workbook = new XSSFWorkbook(); //create blank workbook
            var spreadsheet = workbook.createSheet( " Prospective Clients "); //create first spreadsheet
            var spreadsheet2 = workbook.createSheet(" Investors "); //create second spreadsheet

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
        var townSquare = new FileSorter();
        var damacHills = new FileSorter();

        var damacHillsInFile = new File("/Users/yelderiny/Intelligence/DreamCatcher/Data/Damac Hills/DAMAC Hills (2020) copy.xlsx");
        var damacHillsOutFile = new File("/Users/yelderiny/Intelligence/DreamCatcher/Database Filter/DAMAC Hills Owners.xlsx");

        var townSquareInFile = new File("/Users/yelderiny/Intelligence/DreamCatcher/Data/Town Square/TOWN_SQUARE_DUBAI.xlsx");
        var townSQuareOutFile = new File("/Users/yelderiny/Intelligence/DreamCatcher/Data/Town Square/townSquare_filtered.xlsx");


        townSquare.readExcel(townSquareInFile,0, "p");
        townSquare.readExcel(townSquareInFile,1, "o");
        townSquare.readExcel(townSquareInFile,2, "o");

        townSquare.createExcelFile(townSQuareOutFile);

//        damacHills.readExcel(damacHillsInFile, 0, "p");
//        damacHills.readExcel(damacHillsInFile, 1, "o");
//
//        damacHills.createExcelFile(damacHillsOutFile);
    }
}
