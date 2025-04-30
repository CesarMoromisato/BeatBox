import javax.sound.midi.*;
import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static javax.sound.midi.ShortMessage.*;

public class BeatBox {

    private JFrame frame;

    private JList<String> incomingList;
    private JTextArea userMessage;
    private ArrayList<JCheckBox> checkboxList;
    private JPanel rightPanel;

    private Vector<String> listVector = new Vector<>();
    private HashMap<String,boolean[]> otherSeqsMap = new HashMap<>();

    private String userName;
    private int nextNum;

    private ObjectOutputStream out;
    private ObjectInputStream in;

    private Sequencer sequencer;
    private Sequence sequence;
    private Track track;

    String[] instrumentNames = {"Bass Drum", "Closed Hi-Hat", "Open Hi-Hat", "Acoustic Snare",
            "Crash Cymbal", "Hand Clap", "High Tom", "High Bongo", "Maracas", "Whistle", "Low Conga",
            "Cowbell", "Vibraslap", "Low-mid Tom", "High Agogo", "Open Hi Conga"};

    int[] instruments = {35, 42, 46, 38, 49, 39, 50, 60, 70, 72, 64, 56, 58, 47, 67, 63};

    public static void main(String[] args) {
        new BeatBox().startUp(args[0]);
    }

    public void startUp(String name){
        userName = name;
        try{
            Socket socket = new Socket("127.0.0.1", 5000);
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());
            ExecutorService executor = Executors.newSingleThreadExecutor();
            executor.submit(new RemoteReader());
        } catch(Exception ex){
            System.out.println("Coundn't connect-you'll have to play alone.");
        }
        setUpMidi();
        buildGUI();
    }

    public void buildGUI(){
        frame = new JFrame("Cyber BeatBox");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        JPanel background = new JPanel(new BorderLayout());
        background.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));

        rightPanel = new JPanel(new GridLayout(3,0));
        rightPanel.setBorder(BorderFactory.createEmptyBorder(0,4,0,0));

        GridLayout buttonBoxLayout = new GridLayout(5,0);
        buttonBoxLayout.setVgap(3);

        JPanel buttonBox = new JPanel(buttonBoxLayout);

        JButton start = new JButton("Start");
        start.addActionListener(e -> buildTrackAndStart());
        buttonBox.add(start);

        JButton stop = new JButton("Stop");
        stop.addActionListener(e -> sequencer.stop());
        buttonBox.add(stop);

        JButton upTempo = new JButton("Tempo Up");
        upTempo.addActionListener(e -> changeTempo(1.03f));
        buttonBox.add(upTempo);

        JButton downTempo = new JButton("Tempo Down");
        downTempo.addActionListener(e -> changeTempo(0.97f));
        buttonBox.add(downTempo);

        JButton sendIt = new JButton("Send it");
        sendIt.addActionListener(e -> sendMessageAndTracks());
        buttonBox.add(sendIt);

        rightPanel.add(buttonBox);


        incomingList = new JList<>();
        incomingList.addListSelectionListener( new MyListSelectionListener());
        incomingList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane theList = new JScrollPane(incomingList);
        rightPanel.add(theList);
        incomingList.setListData(listVector);

        userMessage = new JTextArea("Type Here and click Send It");
        userMessage.setLineWrap(true);
        userMessage.setWrapStyleWord(true);
        JScrollPane messageScroller = new JScrollPane(userMessage);
        rightPanel.add(messageScroller);


        Box nameBox = new Box(BoxLayout.Y_AXIS);
        for(String intrumentName : instrumentNames){
            JLabel intrumentLabel = new JLabel(intrumentName);
            intrumentLabel.setBorder(BorderFactory.createEmptyBorder(5,1,7,1));
            nameBox.add(intrumentLabel);
        }

        background.add(BorderLayout.EAST, rightPanel);
        background.add(BorderLayout.WEST, nameBox);

        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("File");
        JMenuItem saveMenuItem = new JMenuItem("Save");
        saveMenuItem.addActionListener(e -> saveBeat());
        JMenuItem loadMenuItem = new JMenuItem("Load");
        loadMenuItem.addActionListener(e -> loadBeat());
        fileMenu.add(loadMenuItem);
        fileMenu.add(saveMenuItem);
        menuBar.add(fileMenu);
        frame.setJMenuBar(menuBar);

        frame.getContentPane().add(background);
        GridLayout grid = new GridLayout(16,16);
        grid.setVgap(1);
        grid.setHgap(2);

        JPanel mainPanel = new JPanel(grid);
        background.add(BorderLayout.CENTER, mainPanel);

        checkboxList = new ArrayList<>();
        for(int i = 0; i < 256; i++){
            JCheckBox c = new JCheckBox();
            c.setSelected(false);
            checkboxList.add(c);
            mainPanel.add(c);
        }
        frame.setBounds(50,50,300,300);
        frame.pack();
        frame.setVisible(true);
    }

    private void setUpMidi(){
        try{
            sequencer = MidiSystem.getSequencer();
            sequencer.open();
            sequence = new Sequence(Sequence.PPQ, 4);
            track = sequence.createTrack();
            sequencer.setTempoInBPM(120);
        } catch(Exception e){
            e.printStackTrace();
        }
    }

    private void buildTrackAndStart(){
        ArrayList<Integer> trackList;
        sequence.deleteTrack(track);
        track = sequence.createTrack();
        for(int i = 0; i < 16; i++){
            trackList = new ArrayList<>();
            int key = instruments[i];
            for(int j = 0; j < 16; j++){
                JCheckBox jc = checkboxList.get(j + (16 * i));
                if(jc.isSelected()){
                    trackList.add(key);
                } else {
                    trackList.add(null);
                }
            }
            makeTracks(trackList);
            track.add(makeEvent(CONTROL_CHANGE, 1, 127, 0, 16));
        }
        track.add(makeEvent(PROGRAM_CHANGE, 9, 1, 0, 15));
        try{
            sequencer.setSequence(sequence);
            sequencer.setLoopCount(sequencer.LOOP_CONTINUOUSLY);
            sequencer.setTempoInBPM(120);
            sequencer.start();
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    private void changeTempo(float tempoMultiplier){
        float tempoFactor = sequencer.getTempoFactor();
        sequencer.setTempoFactor(tempoFactor * tempoMultiplier);
    }

    private void sendMessageAndTracks(){
        boolean[] checkboxState = new boolean[256];
        for(int i = 0; i < 256; i++){
            JCheckBox check = checkboxList.get(i);
            if(check.isSelected()){
                checkboxState[i] = true;
            }
        }
        try{
            out.writeObject(userName + nextNum++ + ": " + userMessage.getText());
            out.writeObject(checkboxState);
        } catch(IOException e){
            System.out.println("Terribly sorry. Could not send it to the server.");
            e.printStackTrace();
        }
        userMessage.setText("");
    }

    public class MyListSelectionListener implements ListSelectionListener{
        public void valueChanged(ListSelectionEvent lse){
            saveBeat();
            if(!lse.getValueIsAdjusting()){
                String selected = incomingList.getSelectedValue();
                if(selected != null){
                    boolean[] selectedState = otherSeqsMap.get(selected);
                    changeSequence(selectedState);
                    sequencer.stop();
                    buildTrackAndStart();
                }
            }
        }
    }
    private void changeSequence(boolean[] checkboxState){
        for(int i = 0; i < 256; i++){
            JCheckBox check = checkboxList.get(i);
            check.setSelected(checkboxState[i]);
        }
    }

    public void makeTracks(ArrayList<Integer> list){
        for(int i = 0; i < list.size(); i++){
            Integer intrumentKey = list.get(i);
            if(intrumentKey != null){
                track.add(makeEvent(NOTE_ON, 9, intrumentKey, 100, i));
                track.add(makeEvent(NOTE_OFF, 9, intrumentKey, 100, i + 1));
            }
        }
    }

    public static MidiEvent makeEvent(int cmd, int chn1, int one, int two, int tick){
        MidiEvent event = null;
        try{
            ShortMessage msg = new ShortMessage();
            msg.setMessage(cmd, chn1, one, two);
            event = new MidiEvent(msg, tick);
        } catch(Exception e){
            e.printStackTrace();
        }
        return event;
    }

    public class RemoteReader implements Runnable{
        public void run(){
            try{
                Object obj;
                while((obj = in.readObject()) != null){
                    System.out.println("got an object from server");
                    System.out.println(obj.getClass());

                    String nameToShow = (String) obj;
                    boolean[] checkboxState = (boolean[]) in.readObject();
                    otherSeqsMap.put(nameToShow, checkboxState);

                    listVector.add(nameToShow);
                    incomingList.setListData(listVector);
                    rightPanel.repaint();
                }
            } catch(IOException | ClassNotFoundException e){
                e.printStackTrace();
            }
        }
    }

    private void saveBeat(){
        JFileChooser fileSave = new JFileChooser();
        fileSave.showSaveDialog(frame);
        boolean[] checkboxState = new boolean[256];
        for(int i = 0; i < 256; i++){
            JCheckBox check = checkboxList.get(i);
            if(check.isSelected()){
                checkboxState[i] = true;
            }
        }
        try(ObjectOutputStream os = new ObjectOutputStream(new FileOutputStream(fileSave.getSelectedFile()))){
            os.writeObject(checkboxState);
        }catch (IOException e){
            e.printStackTrace();
        }
    }

    private void loadBeat(){
        boolean[] checkboxState = null;
        JFileChooser loadSave = new JFileChooser();
        loadSave.showOpenDialog(frame);

        try(ObjectInputStream is = new ObjectInputStream(new FileInputStream(loadSave.getSelectedFile()))){
            checkboxState = (boolean[]) is.readObject();
        } catch (Exception e){
            e.printStackTrace();
        }

        for(int i = 0; i < 256; i++){
            JCheckBox check = checkboxList.get(i);
            check.setSelected(checkboxState[i]);
        }
        sequencer.stop();
        buildTrackAndStart();
    }
}