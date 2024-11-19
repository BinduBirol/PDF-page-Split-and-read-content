package iict.payslip.app;

import javax.swing.*;
import com.itextpdf.text.Document;
import com.itextpdf.text.pdf.*;
import com.itextpdf.text.pdf.parser.PdfTextExtractor;
import java.awt.*;
import java.io.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PdfProcessingWorker extends SwingWorker<Void, File> {
    private File file;
    private JComboBox<String> monthComboBox;
    private JComboBox<Integer> yearComboBox;
    private JProgressBar progressBar;
    private JPanel filePanel;
    private JLabel statusLabel;
    private JButton downloadAllButton;

    public PdfProcessingWorker(File file, JComboBox<String> monthComboBox, JComboBox<Integer> yearComboBox,
                               JProgressBar progressBar, JPanel filePanel, JLabel statusLabel, JButton downloadAllButton) {
        this.file = file;
        this.monthComboBox = monthComboBox;
        this.yearComboBox = yearComboBox;
        this.progressBar = progressBar;
        this.filePanel = filePanel;
        this.statusLabel = statusLabel;
        this.downloadAllButton = downloadAllButton;
    }

    @Override
    protected Void doInBackground() throws Exception {
        File tempDir = new File("temp");
        if (!tempDir.exists()) tempDir.mkdir();

        PdfReader reader = new PdfReader(file.getAbsolutePath());
        int totalPages = reader.getNumberOfPages();
        String month = (String) monthComboBox.getSelectedItem();
        int year = (Integer) yearComboBox.getSelectedItem();

        String idPattern = "ID NO. :\\s*(T\\d{9})";
        Pattern pattern = Pattern.compile(idPattern);

        for (int i = 1; i <= totalPages; i++) {
            String pageText = PdfTextExtractor.getTextFromPage(reader, i);
            Matcher matcher = pattern.matcher(pageText);

            if (matcher.find()) {
                String id = matcher.group(1);
                String fileName = "temp/" + id + "_" + month.toUpperCase() + "_" + year + ".pdf";

                Document document = new Document();
                PdfCopy copy = new PdfCopy(document, new FileOutputStream(fileName));
                document.open();
                PdfImportedPage page = copy.getImportedPage(reader, i);
                copy.addPage(page);
                document.close();

                publish(new File(fileName));
            }

            setProgress((i * 100) / totalPages);
        }
        return null;
    }

    @Override
    protected void process(List<File> files) {
        for (File file : files) {
            JButton downloadButton = new JButton("Download");
            downloadButton.addActionListener(e -> saveFile(file));

            JPanel filePanelItem = new JPanel(new FlowLayout(FlowLayout.LEFT));
            filePanelItem.add(new JLabel(file.getName()));
            filePanelItem.add(downloadButton);
            filePanel.add(filePanelItem);
            filePanel.revalidate();
            filePanel.repaint();
        }
    }

    @Override
    protected void done() {
        SwingUtilities.invokeLater(() -> {
            progressBar.setVisible(false);
            statusLabel.setText("Processing Complete!");
            downloadAllButton.setVisible(true);
        });
    }

    private void saveFile(File file) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setSelectedFile(new File(file.getName()));
        int result = fileChooser.showSaveDialog(null);
        if (result == JFileChooser.APPROVE_OPTION) {
            try {
                File selectedFile = fileChooser.getSelectedFile();
                try (InputStream in = new FileInputStream(file);
                     OutputStream out = new FileOutputStream(selectedFile)) {
                    byte[] buffer = new byte[1024];
                    int bytesRead;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                    }
                    JOptionPane.showMessageDialog(null, "File saved: " + selectedFile.getAbsolutePath());
                }
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(null, "Error saving file: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}
