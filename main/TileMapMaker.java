package dev.main;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Stack;
import javax.imageio.ImageIO;

public class TileMapMaker extends JFrame {
    private static final int TILE_SIZE = 64;
    private int mapWidth = 50;
    private int mapHeight = 50;
    
    // Layer constants
    private static final int LAYER_GROUND = 0;
    private static final int LAYER_DECORATION = 1;
    private static final int LAYER_OBJECTS = 2;
    private static final int NUM_LAYERS = 3;
    
    private int[][][] tileLayers; // [layer][row][col]
    private int currentLayer = LAYER_GROUND;
    private MapPanel mapPanel;
    private MiniMapPanel miniMapPanel;
    private JScrollPane scrollPane;
    private JLabel coordinateLabel;
    private JLabel layerLabel;
    private int currentTile = 0; // 0 = walkable, 1 = solid
    private BufferedImage referenceImage;
    private Stack<TileChange> undoStack = new Stack<>();
    
    // Inner class to store tile changes for undo
    private class TileChange {
        int layer, row, col, oldValue, newValue;
        
        TileChange(int layer, int row, int col, int oldValue, int newValue) {
            this.layer = layer;
            this.row = row;
            this.col = col;
            this.oldValue = oldValue;
            this.newValue = newValue;
        }
    }
    
    public TileMapMaker() {
        // Set system look and feel for native appearance
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        setTitle("2D Tile Map Maker - Multi-Layer");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        
        // Show dialog to set map dimensions
        showDimensionsDialog();
        
        // Initialize tile layers
        tileLayers = new int[NUM_LAYERS][mapHeight][mapWidth];
        
        // Create map panel
        mapPanel = new MapPanel();
        scrollPane = new JScrollPane(mapPanel);
        scrollPane.setPreferredSize(new Dimension(800, 600));
        scrollPane.getViewport().addChangeListener(e -> {
            if (miniMapPanel != null) {
                miniMapPanel.repaint();
            }
        });
        
        // Create mini map panel
        miniMapPanel = new MiniMapPanel();
        
        // Create layered pane to overlay minimap on scroll pane
        JLayeredPane layeredPane = new JLayeredPane();
        layeredPane.setLayout(new OverlayLayout(layeredPane));
        
        // Add scroll pane to bottom layer
        scrollPane.setAlignmentX(0.0f);
        scrollPane.setAlignmentY(0.0f);
        layeredPane.add(scrollPane, JLayeredPane.DEFAULT_LAYER);
        
        // Create panel for minimap positioned at bottom right
        JPanel minimapContainer = new JPanel(new BorderLayout());
        minimapContainer.setOpaque(false);
        minimapContainer.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 10));
        minimapContainer.add(miniMapPanel, BorderLayout.SOUTH);
        
        JPanel minimapWrapper = new JPanel(new BorderLayout());
        minimapWrapper.setOpaque(false);
        minimapWrapper.add(minimapContainer, BorderLayout.EAST);
        minimapWrapper.setAlignmentX(0.0f);
        minimapWrapper.setAlignmentY(0.0f);
        layeredPane.add(minimapWrapper, JLayeredPane.PALETTE_LAYER);
        
        add(layeredPane, BorderLayout.CENTER);
        
        // Create control panel
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        
        // Create coordinate and layer labels
        coordinateLabel = new JLabel("Tile: (0, 0)");
        coordinateLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        coordinateLabel.setFont(new Font("Monospaced", Font.BOLD, 12));
        
        layerLabel = new JLabel("Layer: Ground");
        layerLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        layerLabel.setFont(new Font("Monospaced", Font.BOLD, 12));
        layerLabel.setForeground(new Color(0, 100, 0));
        
        // Layer selection buttons
        JButton groundLayerBtn = new JButton("Ground");
        groundLayerBtn.setBackground(new Color(139, 69, 19));
        groundLayerBtn.setForeground(Color.WHITE);
        groundLayerBtn.setOpaque(true);
        groundLayerBtn.setBorderPainted(true);
        groundLayerBtn.addActionListener(e -> {
            currentLayer = LAYER_GROUND;
            layerLabel.setText("Layer: Ground");
            layerLabel.setForeground(new Color(0, 100, 0));
            mapPanel.repaint();
        });
        
        JButton decorationLayerBtn = new JButton("Decoration");
        decorationLayerBtn.setBackground(new Color(34, 139, 34));
        decorationLayerBtn.setForeground(Color.WHITE);
        decorationLayerBtn.setOpaque(true);
        decorationLayerBtn.setBorderPainted(true);
        decorationLayerBtn.addActionListener(e -> {
            currentLayer = LAYER_DECORATION;
            layerLabel.setText("Layer: Decoration");
            layerLabel.setForeground(new Color(34, 139, 34));
            mapPanel.repaint();
        });
        
        JButton objectsLayerBtn = new JButton("Objects");
        objectsLayerBtn.setBackground(new Color(70, 130, 180));
        objectsLayerBtn.setForeground(Color.WHITE);
        objectsLayerBtn.setOpaque(true);
        objectsLayerBtn.setBorderPainted(true);
        objectsLayerBtn.addActionListener(e -> {
            currentLayer = LAYER_OBJECTS;
            layerLabel.setText("Layer: Objects");
            layerLabel.setForeground(new Color(70, 130, 180));
            mapPanel.repaint();
        });
        
        JButton walkableBtn = new JButton("Walkable (0)");
        walkableBtn.setBackground(Color.GREEN);
        walkableBtn.setOpaque(true);
        walkableBtn.setBorderPainted(true);
        walkableBtn.addActionListener(e -> currentTile = 0);
        
        JButton solidBtn = new JButton("Solid (1)");
        solidBtn.setBackground(Color.RED);
        solidBtn.setOpaque(true);
        solidBtn.setBorderPainted(true);
        solidBtn.addActionListener(e -> currentTile = 1);
        
        JButton loadImageBtn = new JButton("Load Reference Image");
        loadImageBtn.addActionListener(e -> loadReferenceImage());
        
        JButton clearImageBtn = new JButton("Clear Reference");
        clearImageBtn.addActionListener(e -> {
            referenceImage = null;
            mapPanel.repaint();
            miniMapPanel.repaint();
        });
        
        JButton saveBtn = new JButton("Save as TXT");
        saveBtn.addActionListener(e -> saveMap());
        
        JButton saveJsonBtn = new JButton("Save as JSON");
        saveJsonBtn.addActionListener(e -> saveMapAsJson());
        
        JButton loadBtn = new JButton("Load Map");
        loadBtn.addActionListener(e -> loadMap());
        
        JButton clearBtn = new JButton("Clear Layer");
        clearBtn.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(this, 
                "Are you sure you want to clear the current layer?", 
                "Confirm Clear", 
                JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                for (int i = 0; i < mapHeight; i++) {
                    for (int j = 0; j < mapWidth; j++) {
                        tileLayers[currentLayer][i][j] = 0;
                    }
                }
                undoStack.clear();
                mapPanel.repaint();
                miniMapPanel.repaint();
            }
        });
        
        JButton clearAllBtn = new JButton("Clear All Layers");
        clearAllBtn.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(this, 
                "Are you sure you want to clear ALL layers?", 
                "Confirm Clear All", 
                JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                for (int layer = 0; layer < NUM_LAYERS; layer++) {
                    for (int i = 0; i < mapHeight; i++) {
                        for (int j = 0; j < mapWidth; j++) {
                            tileLayers[layer][i][j] = 0;
                        }
                    }
                }
                undoStack.clear();
                mapPanel.repaint();
                miniMapPanel.repaint();
            }
        });
        
        JButton resizeBtn = new JButton("Resize Map");
        resizeBtn.addActionListener(e -> resizeMap());
        
        JButton undoBtn = new JButton("Undo (Right-Click)");
        undoBtn.addActionListener(e -> undo());
        
        controlPanel.add(coordinateLabel);
        controlPanel.add(layerLabel);
        controlPanel.add(new JSeparator(SwingConstants.VERTICAL));
        controlPanel.add(new JLabel("Layers:"));
        controlPanel.add(groundLayerBtn);
        controlPanel.add(decorationLayerBtn);
        controlPanel.add(objectsLayerBtn);
        controlPanel.add(new JSeparator(SwingConstants.VERTICAL));
        controlPanel.add(walkableBtn);
        controlPanel.add(solidBtn);
        controlPanel.add(new JSeparator(SwingConstants.VERTICAL));
        controlPanel.add(undoBtn);
        controlPanel.add(new JSeparator(SwingConstants.VERTICAL));
        controlPanel.add(loadImageBtn);
        controlPanel.add(clearImageBtn);
        controlPanel.add(new JSeparator(SwingConstants.VERTICAL));
        controlPanel.add(saveBtn);
        controlPanel.add(saveJsonBtn);
        controlPanel.add(loadBtn);
        controlPanel.add(new JSeparator(SwingConstants.VERTICAL));
        controlPanel.add(clearBtn);
        controlPanel.add(clearAllBtn);
        controlPanel.add(resizeBtn);
        
        add(controlPanel, BorderLayout.SOUTH);
        
        pack();
        setLocationRelativeTo(null);
    }
    
    private void undo() {
        if (!undoStack.isEmpty()) {
            TileChange change = undoStack.pop();
            tileLayers[change.layer][change.row][change.col] = change.oldValue;
            mapPanel.repaint();
            miniMapPanel.repaint();
        }
    }
    
    private void showDimensionsDialog() {
        JPanel panel = new JPanel(new GridLayout(2, 2, 5, 5));
        JTextField widthField = new JTextField(String.valueOf(mapWidth), 10);
        JTextField heightField = new JTextField(String.valueOf(mapHeight), 10);
        
        panel.add(new JLabel("Map Width:"));
        panel.add(widthField);
        panel.add(new JLabel("Map Height:"));
        panel.add(heightField);
        
        int result = JOptionPane.showConfirmDialog(this, panel, 
            "Set Map Dimensions", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        
        if (result == JOptionPane.OK_OPTION) {
            try {
                int newWidth = Integer.parseInt(widthField.getText().trim());
                int newHeight = Integer.parseInt(heightField.getText().trim());
                
                if (newWidth > 0 && newWidth <= 200 && newHeight > 0 && newHeight <= 200) {
                    mapWidth = newWidth;
                    mapHeight = newHeight;
                } else {
                    JOptionPane.showMessageDialog(this, 
                        "Dimensions must be between 1 and 200", 
                        "Invalid Input", JOptionPane.WARNING_MESSAGE);
                    mapWidth = 50;
                    mapHeight = 50;
                }
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, 
                    "Invalid number format. Using default 50x50", 
                    "Invalid Input", JOptionPane.WARNING_MESSAGE);
                mapWidth = 50;
                mapHeight = 50;
            }
        }
    }
    
    private void resizeMap() {
        JPanel panel = new JPanel(new GridLayout(2, 2, 5, 5));
        JTextField widthField = new JTextField(String.valueOf(mapWidth), 10);
        JTextField heightField = new JTextField(String.valueOf(mapHeight), 10);
        
        panel.add(new JLabel("Map Width:"));
        panel.add(widthField);
        panel.add(new JLabel("Map Height:"));
        panel.add(heightField);
        
        int result = JOptionPane.showConfirmDialog(this, panel, 
            "Resize Map", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        
        if (result == JOptionPane.OK_OPTION) {
            try {
                int newWidth = Integer.parseInt(widthField.getText().trim());
                int newHeight = Integer.parseInt(heightField.getText().trim());
                
                if (newWidth > 0 && newWidth <= 200 && newHeight > 0 && newHeight <= 200) {
                    // Create new tile layers with new dimensions
                    int[][][] newTileLayers = new int[NUM_LAYERS][newHeight][newWidth];
                    
                    // Copy existing data for all layers
                    for (int layer = 0; layer < NUM_LAYERS; layer++) {
                        for (int i = 0; i < Math.min(mapHeight, newHeight); i++) {
                            for (int j = 0; j < Math.min(mapWidth, newWidth); j++) {
                                newTileLayers[layer][i][j] = tileLayers[layer][i][j];
                            }
                        }
                    }
                    
                    mapWidth = newWidth;
                    mapHeight = newHeight;
                    tileLayers = newTileLayers;
                    undoStack.clear();
                    
                    // Update panel
                    mapPanel.setPreferredSize(new Dimension(mapWidth * TILE_SIZE, mapHeight * TILE_SIZE));
                    mapPanel.revalidate();
                    mapPanel.repaint();
                    miniMapPanel.repaint();
                    
                    JOptionPane.showMessageDialog(this, "Map resized successfully!");
                } else {
                    JOptionPane.showMessageDialog(this, 
                        "Dimensions must be between 1 and 200", 
                        "Invalid Input", JOptionPane.WARNING_MESSAGE);
                }
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, 
                    "Invalid number format", 
                    "Invalid Input", JOptionPane.WARNING_MESSAGE);
            }
        }
    }
    
    private void loadReferenceImage() {
        JFileChooser fileChooser = new JFileChooser();
        FileNameExtensionFilter filter = new FileNameExtensionFilter(
            "Image files", "jpg", "jpeg", "png", "gif", "bmp");
        fileChooser.setFileFilter(filter);
        
        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            try {
                referenceImage = ImageIO.read(fileChooser.getSelectedFile());
                mapPanel.repaint();
                miniMapPanel.repaint();
                JOptionPane.showMessageDialog(this, 
                    "Reference image loaded! It will be displayed behind the grid.");
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, 
                    "Error loading image: " + e.getMessage(), 
                    "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    private void saveMap() {
        JFileChooser fileChooser = new JFileChooser();
        FileNameExtensionFilter filter = new FileNameExtensionFilter("Text files", "txt");
        fileChooser.setFileFilter(filter);
        
        int result = fileChooser.showSaveDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            if (!file.getName().endsWith(".txt")) {
                file = new File(file.getAbsolutePath() + ".txt");
            }
            
            try (PrintWriter writer = new PrintWriter(file)) {
                // Write width and height as first line
                writer.println(mapWidth + " " + mapHeight);
                
                // Write each layer
                for (int layer = 0; layer < NUM_LAYERS; layer++) {
                    writer.println("LAYER:" + layer);
                    for (int i = 0; i < mapHeight; i++) {
                        for (int j = 0; j < mapWidth; j++) {
                            writer.print(tileLayers[layer][i][j]);
                            if (j < mapWidth - 1) writer.print(" ");
                        }
                        writer.println();
                    }
                }
                JOptionPane.showMessageDialog(this, "Map saved successfully as TXT!");
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, 
                    "Error saving map: " + e.getMessage(), 
                    "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    private void saveMapAsJson() {
        JFileChooser fileChooser = new JFileChooser();
        FileNameExtensionFilter filter = new FileNameExtensionFilter("JSON files", "json");
        fileChooser.setFileFilter(filter);
        
        int result = fileChooser.showSaveDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            if (!file.getName().endsWith(".json")) {
                file = new File(file.getAbsolutePath() + ".json");
            }
            
            try (PrintWriter writer = new PrintWriter(file)) {
                writer.println("{");
                writer.println("  \"width\": " + mapWidth + ",");
                writer.println("  \"height\": " + mapHeight + ",");
                writer.println("  \"tileSize\": " + TILE_SIZE + ",");
                writer.println("  \"layers\": {");
                
                String[] layerNames = {"ground", "decoration", "objects"};
                
                for (int layer = 0; layer < NUM_LAYERS; layer++) {
                    writer.println("    \"" + layerNames[layer] + "\": [");
                    
                    for (int i = 0; i < mapHeight; i++) {
                        writer.print("      [");
                        for (int j = 0; j < mapWidth; j++) {
                            writer.print(tileLayers[layer][i][j]);
                            if (j < mapWidth - 1) writer.print(", ");
                        }
                        writer.print("]");
                        if (i < mapHeight - 1) writer.println(",");
                        else writer.println();
                    }
                    
                    writer.print("    ]");
                    if (layer < NUM_LAYERS - 1) writer.println(",");
                    else writer.println();
                }
                
                writer.println("  }");
                writer.println("}");
                
                JOptionPane.showMessageDialog(this, "Map saved successfully as JSON!");
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, 
                    "Error saving map: " + e.getMessage(), 
                    "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    private void loadMap() {
        JFileChooser fileChooser = new JFileChooser();
        FileNameExtensionFilter txtFilter = new FileNameExtensionFilter("Text files", "txt");
        FileNameExtensionFilter jsonFilter = new FileNameExtensionFilter("JSON files", "json");
        fileChooser.addChoosableFileFilter(txtFilter);
        fileChooser.addChoosableFileFilter(jsonFilter);
        fileChooser.setFileFilter(txtFilter);
        
        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            String fileName = file.getName().toLowerCase();
            
            if (fileName.endsWith(".json")) {
                loadMapFromJson(file);
            } else {
                loadMapFromTxt(file);
            }
        }
    }
    
    private void loadMapFromTxt(File file) {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            // Read width and height from first line
            String firstLine = reader.readLine();
            if (firstLine != null) {
                String[] dimensions = firstLine.trim().split("\\s+");
                int width = Integer.parseInt(dimensions[0]);
                int height = Integer.parseInt(dimensions[1]);
                
                // Resize map to match loaded dimensions
                mapWidth = width;
                mapHeight = height;
                tileLayers = new int[NUM_LAYERS][mapHeight][mapWidth];
                undoStack.clear();
                
                // Read layer data
                String line;
                int currentLoadLayer = -1;
                int row = 0;
                
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    
                    if (line.startsWith("LAYER:")) {
                        currentLoadLayer = Integer.parseInt(line.substring(6));
                        row = 0;
                    } else if (currentLoadLayer >= 0 && currentLoadLayer < NUM_LAYERS && row < height) {
                        String[] tokens = line.split("\\s+");
                        for (int col = 0; col < Math.min(tokens.length, width); col++) {
                            tileLayers[currentLoadLayer][row][col] = Integer.parseInt(tokens[col]);
                        }
                        row++;
                    }
                }
                
                // Update panel
                mapPanel.setPreferredSize(new Dimension(mapWidth * TILE_SIZE, mapHeight * TILE_SIZE));
                mapPanel.revalidate();
                mapPanel.repaint();
                miniMapPanel.repaint();
                
                JOptionPane.showMessageDialog(this, "Map loaded successfully from TXT!");
            }
        } catch (IOException | NumberFormatException e) {
            JOptionPane.showMessageDialog(this, 
                "Error loading map: " + e.getMessage(), 
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void loadMapFromJson(File file) {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            StringBuilder jsonContent = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonContent.append(line.trim());
            }
            
            String json = jsonContent.toString();
            
            // Parse JSON manually (simple parsing without external libraries)
            int width = extractJsonInt(json, "width");
            int height = extractJsonInt(json, "height");
            
            // Resize map to match loaded dimensions
            mapWidth = width;
            mapHeight = height;
            tileLayers = new int[NUM_LAYERS][mapHeight][mapWidth];
            undoStack.clear();
            
            // Extract each layer
            String[] layerNames = {"ground", "decoration", "objects"};
            
            for (int layer = 0; layer < NUM_LAYERS; layer++) {
                String layerKey = "\"" + layerNames[layer] + "\":";
                int layerStart = json.indexOf(layerKey);
                
                if (layerStart != -1) {
                    int arrayStart = json.indexOf("[", layerStart);
                    int arrayEnd = findMatchingBracket(json, arrayStart);
                    
                    if (arrayStart != -1 && arrayEnd != -1) {
                        String layerContent = json.substring(arrayStart + 1, arrayEnd);
                        
                        // Parse each row
                        int row = 0;
                        int currentPos = 0;
                        
                        while (currentPos < layerContent.length() && row < height) {
                            int rowStart = layerContent.indexOf("[", currentPos);
                            if (rowStart == -1) break;
                            
                            int rowEnd = layerContent.indexOf("]", rowStart);
                            if (rowEnd == -1) break;
                            
                            String rowContent = layerContent.substring(rowStart + 1, rowEnd);
                            String[] values = rowContent.split(",");
                            
                            for (int col = 0; col < Math.min(values.length, width); col++) {
                                tileLayers[layer][row][col] = Integer.parseInt(values[col].trim());
                            }
                            
                            row++;
                            currentPos = rowEnd + 1;
                        }
                    }
                }
            }
            
            // Update panel
            mapPanel.setPreferredSize(new Dimension(mapWidth * TILE_SIZE, mapHeight * TILE_SIZE));
            mapPanel.revalidate();
            mapPanel.repaint();
            miniMapPanel.repaint();
            
            JOptionPane.showMessageDialog(this, "Map loaded successfully from JSON!");
            
        } catch (IOException | NumberFormatException e) {
            JOptionPane.showMessageDialog(this, 
                "Error loading JSON map: " + e.getMessage(), 
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private int findMatchingBracket(String str, int openPos) {
        int count = 1;
        for (int i = openPos + 1; i < str.length(); i++) {
            if (str.charAt(i) == '[') count++;
            else if (str.charAt(i) == ']') {
                count--;
                if (count == 0) return i;
            }
        }
        return -1;
    }
    
    private int extractJsonInt(String json, String key) {
        String searchKey = "\"" + key + "\":";
        int keyIndex = json.indexOf(searchKey);
        if (keyIndex == -1) {
            throw new NumberFormatException("Key '" + key + "' not found in JSON");
        }
        
        int valueStart = keyIndex + searchKey.length();
        int valueEnd = valueStart;
        
        // Skip whitespace
        while (valueEnd < json.length() && Character.isWhitespace(json.charAt(valueEnd))) {
            valueEnd++;
        }
        valueStart = valueEnd;
        
        // Find end of number
        while (valueEnd < json.length() && Character.isDigit(json.charAt(valueEnd))) {
            valueEnd++;
        }
        
        return Integer.parseInt(json.substring(valueStart, valueEnd));
    }
    
    private class MiniMapPanel extends JPanel {
        private static final int MINIMAP_MAX_SIZE = 200;
        private double scale;
        
        public MiniMapPanel() {
            setPreferredSize(new Dimension(MINIMAP_MAX_SIZE + 10, MINIMAP_MAX_SIZE + 10));
            setOpaque(true);
            setBackground(new Color(255, 255, 255, 230));
            setBorder(BorderFactory.createLineBorder(Color.GRAY, 2));
            
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    handleMiniMapClick(e);
                }
            });
            
            addMouseMotionListener(new MouseAdapter() {
                @Override
                public void mouseDragged(MouseEvent e) {
                    handleMiniMapClick(e);
                }
            });
        }
        
        private void handleMiniMapClick(MouseEvent e) {
            int miniWidth = Math.min(mapWidth, MINIMAP_MAX_SIZE);
            int miniHeight = Math.min(mapHeight, MINIMAP_MAX_SIZE);
            scale = Math.min((double) MINIMAP_MAX_SIZE / mapWidth, (double) MINIMAP_MAX_SIZE / mapHeight);
            
            int offsetX = (MINIMAP_MAX_SIZE - (int)(mapWidth * scale)) / 2 + 5;
            int offsetY = (MINIMAP_MAX_SIZE - (int)(mapHeight * scale)) / 2 + 5;
            
            int clickX = e.getX() - offsetX;
            int clickY = e.getY() - offsetY;
            
            if (clickX >= 0 && clickY >= 0) {
                int mapX = (int)(clickX / scale * TILE_SIZE);
                int mapY = (int)(clickY / scale * TILE_SIZE);
                
                Rectangle viewRect = scrollPane.getViewport().getViewRect();
                int centerX = mapX - viewRect.width / 2;
                int centerY = mapY - viewRect.height / 2;
                
                centerX = Math.max(0, Math.min(centerX, mapPanel.getWidth() - viewRect.width));
                centerY = Math.max(0, Math.min(centerY, mapPanel.getHeight() - viewRect.height));
                
                scrollPane.getViewport().setViewPosition(new Point(centerX, centerY));
            }
        }
        
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            scale = Math.min((double) MINIMAP_MAX_SIZE / mapWidth, (double) MINIMAP_MAX_SIZE / mapHeight);
            int miniWidth = (int)(mapWidth * scale);
            int miniHeight = (int)(mapHeight * scale);
            
            int offsetX = (MINIMAP_MAX_SIZE - miniWidth) / 2 + 5;
            int offsetY = (MINIMAP_MAX_SIZE - miniHeight) / 2 + 5;
            
            // Draw reference image if loaded
            if (referenceImage != null) {
                g2d.drawImage(referenceImage, offsetX, offsetY, miniWidth, miniHeight, this);
            }
            
            // Draw all layers with different transparencies
            Color[] layerColors = {
                new Color(139, 69, 19, 120),    // Ground - brown
                new Color(34, 139, 34, 120),    // Decoration - green
                new Color(70, 130, 180, 120)    // Objects - blue
            };
            
            for (int layer = 0; layer < NUM_LAYERS; layer++) {
                for (int row = 0; row < mapHeight; row++) {
                    for (int col = 0; col < mapWidth; col++) {
                        if (tileLayers[layer][row][col] != 0) {
                            int x = offsetX + (int)(col * scale);
                            int y = offsetY + (int)(row * scale);
                            int w = Math.max(1, (int)scale);
                            int h = Math.max(1, (int)scale);
                            
                            g2d.setColor(layerColors[layer]);
                            g2d.fillRect(x, y, w, h);
                        }
                    }
                }
            }
            
            // Draw viewport rectangle
            Rectangle viewRect = scrollPane.getViewport().getViewRect();
            int viewX = offsetX + (int)(viewRect.x / TILE_SIZE * scale);
            int viewY = offsetY + (int)(viewRect.y / TILE_SIZE * scale);
            int viewW = (int)(viewRect.width / TILE_SIZE * scale);
            int viewH = (int)(viewRect.height / TILE_SIZE * scale);
            
            g2d.setColor(new Color(0, 0, 255, 100));
            g2d.fillRect(viewX, viewY, viewW, viewH);
            g2d.setColor(Color.BLUE);
            g2d.setStroke(new BasicStroke(2));
            g2d.drawRect(viewX, viewY, viewW, viewH);
        }
    }
    
    private class MapPanel extends JPanel {
        public MapPanel() {
            setPreferredSize(new Dimension(mapWidth * TILE_SIZE, mapHeight * TILE_SIZE));
            
            MouseAdapter mouseAdapter = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (SwingUtilities.isRightMouseButton(e)) {
                        undo();
                    } else if (SwingUtilities.isLeftMouseButton(e)) {
                        handleMouseEvent(e);
                    }
                }
                
                @Override
                public void mouseDragged(MouseEvent e) {
                    if (SwingUtilities.isLeftMouseButton(e)) {
                        handleMouseEvent(e);
                    }
                }
                
                private void handleMouseEvent(MouseEvent e) {
                    int col = e.getX() / TILE_SIZE;
                    int row = e.getY() / TILE_SIZE;
                    
                    if (row >= 0 && row < mapHeight && col >= 0 && col < mapWidth) {
                        int oldValue = tileLayers[currentLayer][row][col];
                        if (oldValue != currentTile) {
                            undoStack.push(new TileChange(currentLayer, row, col, oldValue, currentTile));
                            tileLayers[currentLayer][row][col] = currentTile;
                            coordinateLabel.setText(String.format("Tile: (%d, %d)", col, row));
                            repaint();
                            miniMapPanel.repaint();
                        }
                    }
                }
            };
            
            addMouseListener(mouseAdapter);
            addMouseMotionListener(mouseAdapter);
        }
        
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;
            
            // Draw reference image if loaded
            if (referenceImage != null) {
                g2d.drawImage(referenceImage, 0, 0, 
                    mapWidth * TILE_SIZE, mapHeight * TILE_SIZE, this);
            }
            
            // Define colors for each layer
            Color[] layerColors = {
                new Color(139, 69, 19, 150),    // Ground - brown
                new Color(34, 139, 34, 150),    // Decoration - green
                new Color(70, 130, 180, 150)    // Objects - blue
            };
            
            Color[] layerSolidColors = {
                new Color(178, 34, 34, 150),    // Ground solid - dark red
                new Color(255, 0, 0, 150),      // Decoration solid - red
                new Color(139, 0, 0, 150)       // Objects solid - darker red
            };
            
            // Draw all layers
            for (int layer = 0; layer < NUM_LAYERS; layer++) {
                boolean isCurrentLayer = (layer == currentLayer);
                int alpha = isCurrentLayer ? 180 : 80; // Current layer more visible
                
                for (int row = 0; row < mapHeight; row++) {
                    for (int col = 0; col < mapWidth; col++) {
                        int x = col * TILE_SIZE;
                        int y = row * TILE_SIZE;
                        
                        int tileValue = tileLayers[layer][row][col];
                        
                        if (tileValue != 0) {
                            if (tileValue == 1) {
                                // Solid tile
                                Color baseColor = layerSolidColors[layer];
                                g2d.setColor(new Color(
                                    baseColor.getRed(), 
                                    baseColor.getGreen(), 
                                    baseColor.getBlue(), 
                                    alpha
                                ));
                            } else {
                                // Walkable tile
                                Color baseColor = layerColors[layer];
                                g2d.setColor(new Color(
                                    baseColor.getRed(), 
                                    baseColor.getGreen(), 
                                    baseColor.getBlue(), 
                                    alpha
                                ));
                            }
                            g2d.fillRect(x, y, TILE_SIZE, TILE_SIZE);
                        }
                    }
                }
            }
            
            // Draw grid
            g2d.setColor(Color.BLACK);
            for (int i = 0; i <= mapHeight; i++) {
                g2d.drawLine(0, i * TILE_SIZE, mapWidth * TILE_SIZE, i * TILE_SIZE);
            }
            for (int i = 0; i <= mapWidth; i++) {
                g2d.drawLine(i * TILE_SIZE, 0, i * TILE_SIZE, mapHeight * TILE_SIZE);
            }
        }
    }
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new TileMapMaker().setVisible(true);
        });
    }
} 