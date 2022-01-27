import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

//TODO understand and use the Google libPhoneNumber library to validate phone numbers

public class FileSorter
{
    private static final List<Property> allProperties = new ArrayList<>(); //all properties
    private static final List<Owner> allOwners = new ArrayList<>(); //owners without properties
    private static final List<Owner> prospectiveClients = new ArrayList<>(); //owners with properties

    //constraints
    private static final List<String> rejectedOwners = new ArrayList<>(Arrays.asList("bank", "properties", "limited", "investment", "estate", "estates",
            "engineering", "development", "llc", "l.l.c", "(l.l.c)", "ltd.", "ltd",  "finance", "commercial", "co", "h.h.",
            "sheikh", "prince", "princess", "tamweel", "united", "capital", "company", "aal", "h.h.al", "h.e", "p.j.s.c")); //list of owner keywords not allowed in the prospectiveClients list

    /**
     * Extracts all the information from the input Excel file, transforms them into objects, and filters them.
     * @param inFile the Excel file
     * @param index index of the relevant sheet
     * @param type an 'o' or 'p' which indicates whether it's a file of owners or properties
     */
    public static void readExcel(final File inFile, final int index, final String type)
    {
        try (final FileInputStream input = new FileInputStream(inFile))
        {
            final var workbook = new XSSFWorkbook(input); //get workbook from the FileInputStream

            final var sheet = workbook.getSheetAt(index);

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
    private static List<Property> rowToProperty(final XSSFSheet propSheet)
    {
        final List<Property> propertyList = new ArrayList<>(); //return variable
        final var headerIndices = new HashMap<>(cellSeeker(propSheet, "P-NUMBER", "AREA", "PROJECT", "ROOMS DESCRIPTION", "ACTUAL AREA"));

        //traverse every Row with enhance for loop
        for (final Row row : propSheet)
        {
            if (row.getRowNum() == 0) continue;

            final var prop = new Property(); //create new com.dreamcatcherbroker.leadgenerator.Property instance

            //traverse every cell in the row
            for (final Cell cell : row)
            {
                //identify the column and add to the object instance accordingly
                if (cell.getColumnIndex() == headerIndices.get("P-NUMBER"))
                {
                    if (cell.getCellType() == CellType.NUMERIC) prop.setpNum((int) cell.getNumericCellValue());
                    else if (cell.getCellType() == CellType.STRING) prop.setpNum(Integer.parseInt(cell.getStringCellValue()));
                }
                else if (cell.getColumnIndex() == headerIndices.get("AREA") && headerIndices.get("AREA") != -1) prop.setLocation(cell.getStringCellValue());
                else if (cell.getColumnIndex() == headerIndices.get("PROJECT") && headerIndices.get("PROJECT") != -1) prop.setName(cell.getStringCellValue());
                else if (cell.getColumnIndex() == headerIndices.get("ROOMS DESCRIPTION") && headerIndices.get("ROOMS DESCRIPTION") != 1) prop.setBedrooms(cell.getStringCellValue());
                else if (cell.getColumnIndex() == headerIndices.get("ACTUAL AREA") && headerIndices.get("ACTUAL AREA") != -1) prop.setSize(cell.getNumericCellValue());
            }
            propertyList.add(prop); //add the property to the output list
        }
        return Collections.unmodifiableList(propertyList);
    }


    /**
     * Transforms a row into an com.dreamcatcherbroker.leadgenerator.Owner Arraylist from an Excel sheet and filters out primary market owners
     * @param ownSheet current Excel sheet
     * @return an arraylist of com.dreamcatcherbroker.leadgenerator.Owner objects
     */
    private static List<Owner> rowToOwner(final XSSFSheet ownSheet)
    {
        final List<Owner> ownersList = new ArrayList<>(); //return variable
        final var headerIndices = new HashMap<>(cellSeeker(ownSheet, "P-NUMBER", "EMAIL", "NAME", "GENDER", "PHONE", "MOBILE", "SECONDARY MOBILE"));

        //traverse every Row with enhance for loop
        for (final Row row : ownSheet)
        {
            if (row.getRowNum() == 0) continue;

            final var owner = new Owner(); //create new com.dreamcatcherbroker.leadgenerator.Owner instance

            //traverse every cell in the row
            for (final Cell cell : row)
            {
                //identify the column and add to the object instance accordingly
                if (cell.getColumnIndex() == headerIndices.get("P-NUMBER"))
                {
                    if (cell.getCellType() == CellType.NUMERIC) owner.setpNum((int) cell.getNumericCellValue());
                    else if (cell.getCellType() == CellType.STRING) owner.setpNum(Integer.parseInt(cell.getStringCellValue()));
                }
                else if (cell.getColumnIndex() == headerIndices.get("NAME") && headerIndices.get("NAME") != -1) owner.setName(FilterUtils.reformatName(cell.getStringCellValue()));
                else if (cell.getColumnIndex() == headerIndices.get("GENDER") && headerIndices.get("GENDER") != -1) owner.setSex(cell.getStringCellValue());
                else if (cell.getColumnIndex() == headerIndices.get("EMAIL") && headerIndices.get("EMAIL") != -1) setOwnerEmail(owner, cell);
                else if ((cell.getColumnIndex() == headerIndices.get("PHONE") && headerIndices.get("PHONE") != -1) ||
                        (cell.getColumnIndex() == headerIndices.get("MOBILE") && headerIndices.get("MOBILE") != -1) ||
                        (cell.getColumnIndex() == headerIndices.get("SECONDARY MOBILE") && headerIndices.get("SECONDARY MOBILE") != -1)) setPhoneNums(owner, cell);
            }
            if (owner.getName() != null && isQualified(owner)) ownersList.add(owner); //add the owner to the output list if they are qualified
        }
        return Collections.unmodifiableList(ownersList);
    }

    /**
     * Attemps to put all arguments in a hashmap with its associated column index in the excel file sheet
     * @param sheet the sheet in question
     * @param headers the input string headers
     * @return a hashmap of headers and their indices
     */
    private static Map<String, Integer> cellSeeker(final XSSFSheet sheet, final String... headers)
    {
        final var indices = new HashMap<String, Integer>();

        for (final String keyword : headers)
        {
            boolean found = false;
            for (final Cell cell : sheet.getRow(0))
            {
                if (keyword.equalsIgnoreCase(cell.getStringCellValue()))
                {
                    found = true;
                    indices.put(keyword, cell.getColumnIndex());
                }
            }
            if (!found) indices.put(keyword, -1);
        }
        return Collections.unmodifiableMap(indices);
    }

    /**
     * Invoked when creating the owner object from the input sheet, this method searches the input cell for a valid
     * phone number, calls the reformat method to make sure the numbers are proper, then adds the number to the owner
     * object if it is unique and valid
     * @param owner current owner
     * @param cell current cell
     */
    private static void setPhoneNums(final Owner owner, final Cell cell)
    {
        //the cell is not empty and the number is longer than 6 digits
        if (!cell.getStringCellValue().equals("") && cell.getStringCellValue().length() > 6)
        {
            final String num = FilterUtils.reformatNumber(cell.getStringCellValue()); //format the number correctly

            if (FilterUtils.isValidNumber(num) && !owner.getPhoneNums().contains(num)) owner.addPhoneNums(num);
        }
    }

    /**
     * Invoked when creating the owner object from the input sheet, this method searches the input cell for a valid
     * email then adds the email to the owner object
     * @param owner current owner
     * @param cell current cell
     */
    private static void setOwnerEmail(final Owner owner, final Cell cell)
    {
        if (!cell.getStringCellValue().equals(""))
        {
            final String eMail = cell.getStringCellValue().toLowerCase(Locale.ROOT);

            if (FilterUtils.isValidEmail(eMail)) owner.setEmail(eMail);
        }
    }

    /**
     * Builds the prospectiveClients list by matching the owners with their properties. For owners that have multiple
     * properties, that is reflected in the prospectiveClients List
     *
     * Note: this seems inefficient. I don't like it.
     */
    private static void setProperty()
    {
        //traverse the list of owners
        for (final Owner owner : allOwners)
        {
            boolean unique = true; //to track whether an owner is already in the prospectiveClients list

            //traverse the list of properties
            for (final Property prop : allProperties)
            {
                //find the property that belongs to the current owner
                if (owner.getpNum() == prop.getpNum())
                {
                    //traverse the prospectiveClients list
                    for (final Owner prospectiveClient : prospectiveClients)
                    {
                        //the owner is already in the list
                        if (owner.getName().equals(prospectiveClient.getName()))
                        {
                            unique = false; //owner is not unique
                            prospectiveClient.addProperty(prop); //add the property to the existing owner in the prospectiveClient list
                        }

                        //the owner is differently named but has the same phone number
                        else if (FilterUtils.hasCommonElements(owner.getPhoneNums(), prospectiveClient.getPhoneNums()))
                        {
                            unique = false; //owner is not unique

                            final String oldName = prospectiveClient.getName(); //get the old name
                            final String newName = owner.getName(); //get the new name

                            final String oldEmail = prospectiveClient.getEmail();
                            final String newEmail = owner.getEmail();

                            if (!oldName.contains(newName)) prospectiveClient.setName(oldName + " & " + newName); //update name

                            if (oldEmail == null && newEmail != null) prospectiveClient.setEmail(newEmail);
                            else if (oldEmail != null && newEmail != null) prospectiveClient.setEmail(oldEmail + ", " + newEmail);

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
     * Rules out unqualified owners from the allOwners list so that only secondary market owners make it onto that list
     *
     * This function needs to use the google phone number checking library
     * @param owner a property owner
     * @return true if the client isn't a developer or a government official
     */
    private static boolean isQualified(final Owner owner)
    {
        final var lowerCaseNames = new ArrayList<>(List.of(owner.getName().split(" "))); //isolate each word in the name and insert into an arraylist
        lowerCaseNames.replaceAll(String::toLowerCase); //change all names to lower case

        boolean a = FilterUtils.hasCommonElements(lowerCaseNames, rejectedOwners); //owned by a developer/corporation/sheikh
        boolean b = owner.getPhoneNums().isEmpty() && owner.getEmail() == null; //no phone numbers and no e-mails (if either is missing that is fine)

        return !a && !b;
    }

    /**
     * Creates a CSV file from the prospectiveClients extracted by findClients()
     */
    public static void createFile(final String outPath, final String state)
    {
        final var output = Path.of(outPath);

        try (final PrintWriter writer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(Files.newOutputStream(output)))))
        {
            //investors
            if (state.equalsIgnoreCase("i"))
                prospectiveClients.stream().filter(client -> client.getProperties().size() > 1).forEach(writer::println);

            //homeowners
            else if (state.equalsIgnoreCase("ho"))
                prospectiveClients.stream().filter(client -> client.getProperties().size() == 1).forEach(writer::println);

            //all owners
            else if (state.equalsIgnoreCase("all")) prospectiveClients.forEach(writer::println);
        }
        catch (IOException e) { e.printStackTrace(); }
    }

    public static void createFile(final String outPath) { createFile(outPath, "all"); }

    /**
     * Creates an Excel file from the prospectiveClients arraylist
     * @param outFile output location
     */
    public static void createExcelFile(final File outFile)
    {
        try(final FileOutputStream output = new FileOutputStream(outFile))
        {
            final var workbook = new XSSFWorkbook(); //create blank workbook
            final var spreadsheet = workbook.createSheet( " Prospective Clients "); //create first spreadsheet
            final var spreadsheet2 = workbook.createSheet(" Investors "); //create second spreadsheet

            final Map <String, Object[]> clientInfo = new TreeMap<>(); //maps a number index to client information
            final Map <String, Object[]> investorInfo = new TreeMap<>(); //maps a number index to investor information

            clientInfo.put("1", new Object[] {"Name", "Phone Number(s)", "e-Mail", "Property" }); //create the file header row
            investorInfo.put("1", new Object[] {"Name", "Phone Number(s)", "e-Mail", "Properties"}); //create the filer header row

            final List<Owner> homeowners = prospectiveClients.stream().filter(client -> client.getProperties().size() == 1).collect(Collectors.toCollection(ArrayList::new)); //filter out clients with one property
            final List<Owner> investors = prospectiveClients.stream().filter(client -> client.getProperties().size() > 1).collect(Collectors.toCollection(ArrayList::new)); //filter out clients with multiple properties

            //prepare Excel sheets
            ExcelFileHelper(spreadsheet, clientInfo, homeowners);
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
    private static void ExcelFileHelper(final XSSFSheet spreadsheet, final Map<String, Object[]> map, final List<Owner> clients)
    {
        var tracker = new AtomicInteger(2); //tracker (starts at '2' because at '1' is the row header
        Row row;

        //iterate clients
        clients.forEach(owner -> map.put("" + tracker.getAndIncrement(), new Object[] {owner.getName(), owner.getPhoneNums().toString(), owner.getEmail(), owner.getProperties().toString()})); //for each client, map an index to the client information

        //Iterate over data and write to sheet
        final Set <String> keyid = map.keySet(); //extract the indexes of the map
        int rowid = 0; //tracker for the rows

        //iterate the indexes of the map
        for (final String key : keyid)
        {
            row = spreadsheet.createRow(rowid++); //create a row in the spreadsheet
            final Object[] objectArr = map.get(key); //extract the client object list from the map
            int cellid = 0;

            //iterate the client object list
            for (Object obj : objectArr)
            {
                final Cell cell = row.createCell(cellid++); //create a cell for each object in the client object
                cell.setCellValue((String)obj); //write into the cell with the object data
            }
        }
    }

    public static void main(String[] args)
    {
//        var townSquare = new FileSorter();
//        var damacHills = new FileSorter();

//        var damacHillsInFile = new File("/Users/yelderiny/Intelligence/DreamCatcher/Data/Damac Hills/DAMAC Hills (2020) copy.xlsx");
//        var damacHillsOutFile = new File("/Users/yelderiny/Intelligence/DreamCatcher/Database Filter/DAMAC Hills Owners.xlsx");

        var townSquareInFile = new File("/Users/yelderiny/Intelligence/DreamCatcher/Data/Town Square/TOWN_SQUARE_DUBAI.xlsx");
        var townSQuareOutFile = new File("/Users/yelderiny/Intelligence/DreamCatcher/Data/Town Square/townSquare_filtered.xlsx");


        readExcel(townSquareInFile,0, "p");
        readExcel(townSquareInFile,1, "o");
        readExcel(townSquareInFile,2, "o");

        createExcelFile(townSQuareOutFile);



//        damacHills.readExcel(damacHillsInFile, 0, "p");
//        damacHills.readExcel(damacHillsInFile, 1, "o");
//
//        damacHills.createExcelFile(damacHillsOutFile);
    }
}
