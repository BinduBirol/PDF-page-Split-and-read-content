package iict.payslip.app;

import javax.swing.*;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import com.itextpdf.text.Document;
import com.itextpdf.text.pdf.PdfCopy;
import com.itextpdf.text.pdf.PdfImportedPage;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.parser.LocationTextExtractionStrategy;
import com.itextpdf.text.pdf.parser.PdfTextExtractor;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;



public class MainApp extends JFrame {
    private JButton uploadButton, processButton, downloadAllButton;
    private JFileChooser fileChooser;
    private JComboBox<String> monthComboBox;
    private JComboBox<Integer> yearComboBox;
    private JLabel statusLabel;
    private JPanel filePanel;
    private File uploadedFile;
    private JProgressBar progressBar; // Progress bar for PDF processing

    public MainApp() {
        // Set up JFrame
        setTitle("Pay Slips PDF Processor");
        setSize(600, 450);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BoxLayout(getContentPane(), BoxLayout.Y_AXIS));  // Use BoxLayout for vertical stacking

        // Initialize components
        uploadButton = new JButton("Choose PDF");
        processButton = new JButton("Process");
        downloadAllButton = new JButton("Download All as ZIP");
        monthComboBox = new JComboBox<>(new String[]{"January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"});
       
        
        
     // Get the current month and year
        LocalDate today = LocalDate.now();
        int currentMonth = today.getMonthValue(); // Month as 1-12
        int currentYear = today.getYear();       // Current year
        
        Integer[] years = new Integer[6];
        for (int i = 0; i < 6; i++) {
            years[i] = currentYear - i;
        }
        
        yearComboBox = new JComboBox<>(years);

        // Set the default selection for the current month and year
        monthComboBox.setSelectedIndex(currentMonth - 1); // Index is 0-based
        for (int i = 0; i < yearComboBox.getItemCount(); i++) {
            if (yearComboBox.getItemAt(i) == currentYear) {
                yearComboBox.setSelectedIndex(i);
                break;
            }
        }
        
        statusLabel = new JLabel("No file uploaded.");
        filePanel = new JPanel();
        filePanel.setLayout(new BoxLayout(filePanel, BoxLayout.Y_AXIS));
        progressBar = new JProgressBar(0, 100);

        // Set initial properties
        progressBar.setValue(0);
        progressBar.setStringPainted(true);
        progressBar.setPreferredSize(new Dimension(550, 20));
        progressBar.setVisible(false); // Initially hidden

        // File uploader button
        uploadButton.setToolTipText("Choose a PDF file to upload");
        uploadButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                JFileChooser chooser = new JFileChooser();
                chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("PDF Files", "pdf"));
                int returnVal = chooser.showOpenDialog(null);
                if (returnVal == JFileChooser.APPROVE_OPTION) {
                    uploadedFile = chooser.getSelectedFile();
                    statusLabel.setText("Selected file: " + uploadedFile.getName());
                }
            }
        });

        // Process button
        processButton.setToolTipText("Start processing the uploaded PDF");
        processButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (uploadedFile != null) {
                    // Clear previous results before processing a new file
                    filePanel.removeAll(); 
                    filePanel.revalidate(); 
                    filePanel.repaint();

                    showLoadingMessage();

                    new Thread(new Runnable() {
                        public void run() {
                            try {
                                processPDF(uploadedFile);
                            } catch (Exception ex) {
                                ex.printStackTrace();
                                JOptionPane.showMessageDialog(null, "Error during processing: " + ex.getMessage());
                            }
                        }
                    }).start();
                } else {
                    JOptionPane.showMessageDialog(null, "Please upload a PDF file first.");
                }
            }
        });

        // Download All button (Initially hidden)
        downloadAllButton.setVisible(false);
        downloadAllButton.setToolTipText("Download all processed PDFs as a ZIP file");
        downloadAllButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                try {
                    createZipFile(uploadedFile.getName());
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(null, "Error creating ZIP file: " + ex.getMessage());
                }
            }
        });

        // Layout setup for form panel (grid layout)
        JPanel formPanel = new JPanel(new GridLayout(3, 2, 10, 10));
     // Adding margin to formPanel using EmptyBorder (top, left, bottom, right)
        formPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        
        
        formPanel.add(uploadButton);
        formPanel.add(monthComboBox);
        formPanel.add(yearComboBox);
        formPanel.add(processButton);

        // JScrollPane setup for the file panel (results display)
        JScrollPane scrollPane = new JScrollPane(filePanel);
        scrollPane.setPreferredSize(new Dimension(550, 200));  // Set a preferred size for the scroll pane
        
        
        
        // Add components to frame with BoxLayout
        add(formPanel);
        add(statusLabel);
        add(progressBar); // Add progress bar
        add(scrollPane);   // Add the scrollPane below the form panel
        add(downloadAllButton);

        setLocationRelativeTo(null);  // Center the window
    }


    // Show loading message and start progress bar
    private void showLoadingMessage() {
        statusLabel.setText("Processing... Please wait.");
        statusLabel.setForeground(Color.ORANGE);
        progressBar.setVisible(true);
    }

    // Process the PDF file
    private void processPDF(File file) throws Exception {
        // Create the paySliplit/temp directory under user's home directory
        File tempDir = new File(System.getProperty("user.home"), "paySliplit/temp");

        // Check if the directory exists; if not, try to create it
        if (!tempDir.exists()) {
            if (tempDir.mkdirs()) {
                System.out.println("Temp directory created: " + tempDir.getAbsolutePath());
            } else {
                throw new IOException("Failed to create temp directory: " + tempDir.getAbsolutePath());
            }
        } else {
            System.out.println("Temp directory already exists: " + tempDir.getAbsolutePath());
        }

        // Load the PDF document with PDFBox
        try (PDDocument document = PDDocument.load(file)) {
            int totalPages = document.getNumberOfPages();
            String month = (String) monthComboBox.getSelectedItem();
            int year = (Integer) yearComboBox.getSelectedItem();

            // Process each page
            for (int i = 1; i <= totalPages; i++) {
                // Extract ID from the current page
                String id = extractIdFromDocumentWithPDFBox(document, i);

                // Use the ID in the filename or other processing logic
                String fileName;
                if (id != null) {
                    // Use the extracted ID in the filename
                    fileName = new File(tempDir, id + "_" + month.toUpperCase() + "_" + year + ".pdf").getAbsolutePath();
                } else {
                    // Fallback to default naming if no ID found
                    fileName = new File(tempDir, i + "_" + month.toUpperCase() + "_" + year + ".pdf").getAbsolutePath();
                }

                // Save the page to a new PDF file
                Document pdfDocument = new Document();
                PdfCopy copy = new PdfCopy(pdfDocument, new FileOutputStream(fileName));
                pdfDocument.open();
                PdfImportedPage page = copy.getImportedPage(new PdfReader(file.getAbsolutePath()), i);
                copy.addPage(page);
                pdfDocument.close();

                // Update progress bar
                final int progress = (i * 100) / totalPages;
                SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        progressBar.setValue(progress);
                    }
                });

                // Add download button to the list
                final String finalFileName = fileName;
                SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        JButton downloadButton = new JButton("Download");
                        downloadButton.addActionListener(new ActionListener() {
                            public void actionPerformed(ActionEvent e) {
                                downloadFile(finalFileName);
                            }
                        });

                        JPanel filePanelItem = new JPanel(new FlowLayout(FlowLayout.LEFT));
                        filePanelItem.add(new JLabel(finalFileName));
                        filePanelItem.add(downloadButton);
                        filePanel.add(filePanelItem);
                        filePanel.revalidate();
                        filePanel.repaint();
                    }
                });
            }

            // Hide progress bar and show completion message
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    progressBar.setVisible(false);
                    statusLabel.setText("Processing Complete! Total " + totalPages + " pages");
                    statusLabel.setForeground(Color.green);
                    downloadAllButton.setVisible(true);
                }
            });
        }
    }


    // Download individual PDF page
    private void downloadFile(String fileName) {
        File file = new File(fileName);  // File path in the temp_pages directory

        if (file.exists()) {
            try {
                Desktop.getDesktop().open(file);  // Opens the file using the default viewer
            } catch (IOException e) {
                JOptionPane.showMessageDialog(null, "Error opening file: " + e.getMessage());
            }
        } else {
            JOptionPane.showMessageDialog(null, "File not found: " + fileName);
        }
    }


    // Create a ZIP file containing all split pages
    private void createZipFile(String filename) throws IOException {
        // Define the temp directory where the files are saved
        File tempDir = new File(System.getProperty("user.home"), "paySliplit/temp");
        
        // Check if the directory exists and contains files
        File[] files = tempDir.listFiles();
        if (files == null || files.length == 0) {
            throw new IOException("No files to zip in the temp directory: " + tempDir.getAbsolutePath());
        }

        // Create the ZIP file at the specified location
        File zipFile = new File(System.getProperty("user.home"), "paySliplit/" + filename + ".zip");

        // Create the ZIP file and add the files
        try (FileOutputStream fos = new FileOutputStream(zipFile);
             ZipOutputStream zipOut = new ZipOutputStream(fos)) {
            byte[] buffer = new byte[1024];

            // Loop through the files in the temp directory and add them to the ZIP
            for (File file : files) {
                try (FileInputStream fis = new FileInputStream(file)) {
                    // Create a ZipEntry for each file
                    ZipEntry zipEntry = new ZipEntry(file.getName());
                    zipOut.putNextEntry(zipEntry);

                    // Read the file and write to the ZIP output stream
                    int length;
                    while ((length = fis.read(buffer)) > 0) {
                        zipOut.write(buffer, 0, length);
                    }

                    // Close the entry
                    zipOut.closeEntry();
                }
            }
        }

        // Notify user and offer actions for the ZIP file
        String zipFilePath = zipFile.getAbsolutePath();
        JOptionPane.showMessageDialog(null, "ZIP file created at: " + zipFilePath);

        // Ask user whether they want to open the ZIP file now
        int option = JOptionPane.showConfirmDialog(null, "Do you want to open the ZIP file now?",
                                                   "Open ZIP File", JOptionPane.YES_NO_OPTION);
        if (option == JOptionPane.YES_OPTION) {
            try {
                Desktop.getDesktop().open(zipFile);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(null, "Unable to open the ZIP file.");
            }
        } else {
            // Provide a dialog to save the ZIP file
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("Save ZIP File");
            fileChooser.setSelectedFile(zipFile);
            int result = fileChooser.showSaveDialog(null);

            if (result == JFileChooser.APPROVE_OPTION) {
                File selectedFile = fileChooser.getSelectedFile();
                if (!selectedFile.exists() || selectedFile.renameTo(selectedFile)) {
                    Files.copy(zipFile.toPath(), selectedFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    JOptionPane.showMessageDialog(null, "File saved successfully!");
                } else {
                    JOptionPane.showMessageDialog(null, "Failed to save the file.");
                }
            }
        }

        // Cleanup temporary files
        cleanupTempFiles(tempDir);
    }



    // Cleanup the temporary files after zipping
    private void cleanupTempFiles(File tempDir) {
        File[] files = tempDir.listFiles();
        if (files != null) {
            for (File file : files) {
                file.delete();
            }
        }
        tempDir.delete();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                new MainApp().setVisible(true);
            }
        });
    }
    
    private String extractIdFromDocumentWithPDFBox(PDDocument document, int pageNumber) {
        try {
            // Use PDFBox to extract text from the page
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(pageNumber);  // Set the start page
            stripper.setEndPage(pageNumber);    // Set the end page (same as start for single page)

            String pageText = stripper.getText(document);  // Extract text from the page

            // Define the regex to capture the ID
            String regex = "ID NO\\. : ([^,]+)";  // Regex to capture ID value before the comma
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(pageText);

            if (matcher.find()) {
                return matcher.group(1);  // Return the captured ID
            } else {
                return null;  // Return null if no ID is found
            }
        } catch (IOException e) {
            e.printStackTrace();  // Handle any PDFBox related errors
        }
        return null;  // Return null in case of error
    }



}
