package tubeforge;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

public final class TubeForgeFrame extends JFrame {
    private final FFmpegService ffmpeg = new FFmpegService();
    private final RecordingService recorder = new RecordingService();
    private Project project = new Project();
    private final DefaultListModel<MediaClip> model = new DefaultListModel<>();
    private final JList<MediaClip> timeline = new JList<>(model);
    private final PreviewPanel preview = new PreviewPanel();
    private final JSpinner in = timeSpinner(), out = timeSpinner();
    private final JTextField title = new JTextField(), channel = new JTextField();
    private final JComboBox<String> preset = new JComboBox<>(new String[]{
            "YouTube 1080p (16:9)", "YouTube 1440p (16:9)",
            "YouTube 4K (16:9)", "YouTube Shorts 1080x1920 (9:16)"});
    private final JLabel status = new JLabel("Ready — import clips to start");
    private final JProgressBar progress = new JProgressBar();
    private final JButton recordButton = new JButton("● Record");
    private final Timer recordingTimer;
    private Instant recordingStarted;
    private Path recordingFile;

    public TubeForgeFrame() {
        super("TubeForge — YouTube Video Editor");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1120, 720));
        setSize(1380, 860);
        setLocationRelativeTo(null);
        getContentPane().setBackground(new Color(18, 20, 27));
        setJMenuBar(menu());

        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBorder(new EmptyBorder(14, 14, 14, 14));
        root.setBackground(new Color(18, 20, 27));
        root.add(toolbar(), BorderLayout.NORTH);
        root.add(workspace(), BorderLayout.CENTER);
        root.add(statusbar(), BorderLayout.SOUTH);
        setContentPane(root);
        timeline.addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) selectClip(); });
        recordButton.setForeground(new Color(235, 70, 90));
        recordButton.setFocusPainted(false);
        recordButton.addActionListener(e -> toggleRecording());
        recordingTimer = new Timer(1000, e -> updateRecordingTime());
    }

    private JMenuBar menu() {
        JMenuBar bar = new JMenuBar();
        JMenu file = new JMenu("File");
        file.add(item("New project", e -> reset()));
        file.add(item("Open project…", e -> openProject()));
        file.add(item("Save project…", e -> saveProject()));
        file.addSeparator();
        file.add(item("Import clips…", e -> importClips()));
        file.add(item("Export video…", e -> export()));
        bar.add(file);
        return bar;
    }

    private JMenuItem item(String text, java.awt.event.ActionListener action) {
        JMenuItem item = new JMenuItem(text); item.addActionListener(action); return item;
    }

    private JPanel toolbar() {
        JPanel bar = panel(new FlowLayout(FlowLayout.LEFT, 9, 6));
        bar.add(button("＋ Import clips", new Color(63, 94, 251), this::importClips));
        bar.add(button("↑", null, () -> move(-1)));
        bar.add(button("↓", null, () -> move(1)));
        bar.add(button("Remove", null, this::remove));
        bar.add(Box.createHorizontalStrut(18));
        bar.add(recordButton);
        bar.add(button("▶ Export video", new Color(225, 48, 73), this::export));
        return bar;
    }

    private JComponent workspace() {
        JSplitPane left = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, library(), previewArea());
        left.setResizeWeight(.25); left.setDividerLocation(300); left.setBorder(null);
        JSplitPane all = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, inspector());
        all.setResizeWeight(.79); all.setDividerLocation(1050); all.setBorder(null);
        return all;
    }

    private JPanel library() {
        JPanel p = darkPanel(new BorderLayout(8, 8));
        p.add(sectionTitle("TIMELINE"), BorderLayout.NORTH);
        timeline.setBackground(new Color(27, 30, 40));
        timeline.setForeground(Color.WHITE);
        timeline.setSelectionBackground(new Color(63, 94, 251));
        timeline.setFixedCellHeight(48);
        timeline.setBorder(new EmptyBorder(6, 6, 6, 6));
        p.add(new JScrollPane(timeline), BorderLayout.CENTER);
        return p;
    }

    private JPanel previewArea() {
        JPanel p = darkPanel(new BorderLayout(8, 8));
        p.add(sectionTitle("PROGRAM MONITOR"), BorderLayout.NORTH);
        p.add(preview, BorderLayout.CENTER);
        JLabel hint = new JLabel("Select a timeline clip to preview its midpoint", SwingConstants.CENTER);
        hint.setForeground(new Color(155, 160, 177));
        p.add(hint, BorderLayout.SOUTH);
        return p;
    }

    private JPanel inspector() {
        JPanel p = darkPanel(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0; g.weightx = 1; g.fill = GridBagConstraints.HORIZONTAL;
        g.insets = new Insets(6, 8, 6, 8);
        g.gridy = 0; p.add(sectionTitle("EDIT CLIP"), g);
        g.gridy++; p.add(label("In point (seconds)"), g);
        g.gridy++; p.add(in, g);
        g.gridy++; p.add(label("Out point (seconds)"), g);
        g.gridy++; p.add(out, g);
        g.gridy++; p.add(button("Apply trim", new Color(63, 94, 251), this::applyTrim), g);
        g.gridy++; g.insets.top = 22; p.add(sectionTitle("VIDEO DETAILS"), g); g.insets.top = 6;
        g.gridy++; p.add(label("Video title"), g);
        g.gridy++; p.add(title, g);
        g.gridy++; p.add(label("Channel name"), g);
        g.gridy++; p.add(channel, g);
        g.gridy++; p.add(label("Export preset"), g);
        g.gridy++; p.add(preset, g);
        g.gridy++; g.weighty = 1; p.add(Box.createVerticalGlue(), g);
        return p;
    }

    private JPanel statusbar() {
        JPanel p = panel(new BorderLayout(12, 0));
        status.setForeground(new Color(185, 190, 205));
        progress.setVisible(false);
        p.add(status, BorderLayout.CENTER); p.add(progress, BorderLayout.EAST);
        return p;
    }

    private void importClips() {
        JFileChooser chooser = new JFileChooser();
        chooser.setMultiSelectionEnabled(true);
        chooser.setFileFilter(new FileNameExtensionFilter("Video files", "mp4", "mov", "mkv", "avi", "webm", "m4v"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        setBusy("Reading media…");
        new SwingWorker<Void, MediaClip>() {
            protected Void doInBackground() throws Exception {
                for (File f : chooser.getSelectedFiles())
                    publish(new MediaClip(f.toPath(), ffmpeg.probeDuration(f.toPath())));
                return null;
            }
            protected void process(List<MediaClip> clips) {
                for (MediaClip c : clips) { project.clips.add(c); model.addElement(c); }
            }
            protected void done() {
                clearBusy(); try { get(); status.setText(model.size() + " clips in timeline");
                } catch (Exception ex) { error(ex); }
                if (!model.isEmpty()) timeline.setSelectedIndex(model.size() - 1);
            }
        }.execute();
    }

    private void selectClip() {
        MediaClip c = timeline.getSelectedValue();
        if (c == null) return;
        in.setValue(c.inPoint()); out.setValue(c.outPoint());
        preview.setMessage("Generating preview…");
        new SwingWorker<BufferedImage, Void>() {
            protected BufferedImage doInBackground() throws Exception { return ffmpeg.thumbnail(c); }
            protected void done() {
                try { preview.setImage(get()); } catch (Exception e) { preview.setMessage("Preview unavailable"); }
            }
        }.execute();
    }

    private void applyTrim() {
        MediaClip c = timeline.getSelectedValue();
        if (c == null) { status.setText("Select a clip first"); return; }
        try {
            c.setTrim(((Number) in.getValue()).doubleValue(), ((Number) out.getValue()).doubleValue());
            timeline.repaint(); status.setText("Trim applied — duration " + MediaClip.time(c.editedDuration()));
            selectClip();
        } catch (IllegalArgumentException e) { error(e); }
    }

    private void move(int direction) {
        int from = timeline.getSelectedIndex(), to = from + direction;
        if (from < 0 || to < 0 || to >= model.size()) return;
        MediaClip clip = project.clips.remove(from); project.clips.add(to, clip);
        model.remove(from); model.add(to, clip); timeline.setSelectedIndex(to);
    }

    private void remove() {
        int i = timeline.getSelectedIndex(); if (i < 0) return;
        project.clips.remove(i); model.remove(i); preview.setMessage("Select a clip");
    }

    private void export() {
        if (project.clips.isEmpty()) { error(new Exception("Import at least one video clip.")); return; }
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("youtube-video.mp4"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        syncDetails(); setBusy("Preparing export…");
        new SwingWorker<Void, String>() {
            protected Void doInBackground() throws Exception {
                ffmpeg.export(project, chooser.getSelectedFile().toPath(), this::publish); return null;
            }
            protected void process(List<String> messages) { status.setText(messages.getLast()); }
            protected void done() {
                clearBusy();
                try { get(); status.setText("Export complete: " + chooser.getSelectedFile().getName());
                    JOptionPane.showMessageDialog(TubeForgeFrame.this, "Your YouTube video is ready:\n"
                            + chooser.getSelectedFile(), "Export complete", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception e) { error(e); }
            }
        }.execute();
    }

    private void toggleRecording() {
        if (recorder.isRecording()) {
            stopRecording();
            return;
        }
        JComboBox<RecordingService.Source> source = new JComboBox<>(RecordingService.Source.values());
        JCheckBox microphone = new JCheckBox("Record microphone audio", true);
        JSlider microphoneVolume = new JSlider(0, 200, 100);
        microphoneVolume.setMajorTickSpacing(50);
        microphoneVolume.setPaintTicks(true);
        JLabel volumeLabel = new JLabel("Microphone volume: 100%");
        microphoneVolume.addChangeListener(e ->
                volumeLabel.setText("Microphone volume: " + microphoneVolume.getValue() + "%"));
        microphone.addActionListener(e -> {
            microphoneVolume.setEnabled(microphone.isSelected());
            volumeLabel.setEnabled(microphone.isSelected());
        });
        JPanel options = new JPanel(new GridLayout(0, 1, 4, 4));
        options.add(new JLabel("What would you like to record?"));
        options.add(source);
        options.add(microphone);
        options.add(volumeLabel);
        options.add(microphoneVolume);
        int answer = JOptionPane.showConfirmDialog(this, options, "Start recording",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (answer != JOptionPane.OK_OPTION) return;

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save recording as");
        chooser.setSelectedFile(new File("recording-" + System.currentTimeMillis() + ".mp4"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        recordingFile = chooser.getSelectedFile().toPath();
        try {
            recorder.start((RecordingService.Source) source.getSelectedItem(),
                    microphone.isSelected(), microphoneVolume.getValue(), recordingFile);
            recordingStarted = Instant.now();
            recordingTimer.start();
            recordButton.setText("■ Stop  00:00");
            recordButton.setForeground(Color.WHITE);
            recordButton.setBackground(new Color(210, 40, 58));
            status.setText("Recording — return to TubeForge and press Stop when finished");
        } catch (Exception e) {
            error(e);
        }
    }

    private void updateRecordingTime() {
        if (recordingStarted == null) return;
        long seconds = Duration.between(recordingStarted, Instant.now()).toSeconds();
        recordButton.setText("■ Stop  %02d:%02d".formatted(seconds / 60, seconds % 60));
    }

    private void stopRecording() {
        recordButton.setEnabled(false);
        recordingTimer.stop();
        status.setText("Finishing recording…");
        new SwingWorker<MediaClip, Void>() {
            protected MediaClip doInBackground() throws Exception {
                recorder.stop();
                return new MediaClip(recordingFile, ffmpeg.probeDuration(recordingFile));
            }
            protected void done() {
                recordButton.setEnabled(true);
                recordButton.setText("● Record");
                recordButton.setForeground(new Color(235, 70, 90));
                recordButton.setBackground(UIManager.getColor("Button.background"));
                recordingStarted = null;
                try {
                    MediaClip clip = get();
                    project.clips.add(clip);
                    model.addElement(clip);
                    timeline.setSelectedIndex(model.size() - 1);
                    status.setText("Recording added to the timeline");
                } catch (Exception e) {
                    error(e);
                }
            }
        }.execute();
    }

    private void saveProject() {
        JFileChooser c = new JFileChooser(); c.setSelectedFile(new File("video.tforge"));
        if (c.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try { syncDetails(); project.save(c.getSelectedFile().toPath()); status.setText("Project saved");
        } catch (Exception e) { error(e); }
    }

    private void openProject() {
        JFileChooser c = new JFileChooser();
        c.setFileFilter(new FileNameExtensionFilter("TubeForge project", "tforge"));
        if (c.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            project = Project.load(c.getSelectedFile().toPath()); refresh();
            status.setText("Project opened");
        } catch (Exception e) { error(e); }
    }

    private void reset() { project = new Project(); refresh(); status.setText("New project"); }
    private void refresh() {
        model.clear(); project.clips.forEach(model::addElement);
        title.setText(project.title); channel.setText(project.channel); preset.setSelectedItem(project.preset);
        preview.setMessage("Select a clip");
    }
    private void syncDetails() {
        project.title = title.getText().trim(); project.channel = channel.getText().trim();
        project.preset = String.valueOf(preset.getSelectedItem());
    }
    private void setBusy(String text) { status.setText(text); progress.setIndeterminate(true); progress.setVisible(true); }
    private void clearBusy() { progress.setVisible(false); progress.setIndeterminate(false); }
    private void error(Exception e) {
        clearBusy(); Throwable cause = e.getCause() != null ? e.getCause() : e;
        status.setText("Something went wrong");
        JOptionPane.showMessageDialog(this, cause.getMessage(), "TubeForge", JOptionPane.ERROR_MESSAGE);
    }

    private static JSpinner timeSpinner() {
        JSpinner s = new JSpinner(new SpinnerNumberModel(0.0, 0.0, 86400.0, 0.1));
        s.setEditor(new JSpinner.NumberEditor(s, "0.0")); return s;
    }
    private static JPanel panel(LayoutManager l) { JPanel p = new JPanel(l); p.setOpaque(false); return p; }
    private static JPanel darkPanel(LayoutManager l) {
        JPanel p = new JPanel(l); p.setBackground(new Color(23, 25, 34));
        p.setBorder(new CompoundBorder(new LineBorder(new Color(42, 46, 60)), new EmptyBorder(10,10,10,10))); return p;
    }
    private static JLabel label(String t) { JLabel l = new JLabel(t); l.setForeground(new Color(190,195,210)); return l; }
    private static JLabel sectionTitle(String t) { JLabel l = label(t); l.setFont(l.getFont().deriveFont(Font.BOLD, 12)); return l; }
    private static JButton button(String text, Color color, Runnable action) {
        JButton b = new JButton(text); b.setFocusPainted(false);
        if (color != null) { b.setBackground(color); b.setForeground(Color.WHITE); }
        b.addActionListener(e -> action.run()); return b;
    }

    private static final class PreviewPanel extends JPanel {
        private BufferedImage image; private String message = "Import clips to begin";
        PreviewPanel() { setBackground(Color.BLACK); }
        void setImage(BufferedImage image) { this.image = image; repaint(); }
        void setMessage(String m) { image = null; message = m; repaint(); }
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (image != null) {
                double scale = Math.min(getWidth() / (double) image.getWidth(), getHeight() / (double) image.getHeight());
                int w = (int)(image.getWidth()*scale), h = (int)(image.getHeight()*scale);
                g.drawImage(image, (getWidth()-w)/2, (getHeight()-h)/2, w, h, null);
            } else {
                g.setColor(new Color(140,145,160)); FontMetrics fm = g.getFontMetrics();
                g.drawString(message, (getWidth()-fm.stringWidth(message))/2, getHeight()/2);
            }
        }
    }
}
