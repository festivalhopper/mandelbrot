/**
 * @author Michel Pluess
 * 
 * Usage:
 * 
 * Explorer Mode:
 * - Make a square-shaped selection with the left mouse button, going from
 *   top-left to bottom-right of the area you want to enlarge. New image
 *   will be calculated and displayed.
 * - Right-click to get back to the last image (you can go back until the initial
 *   image if you want to).
 * - Type "s" to save the current image to the "screenshots" directory.
 */

package ch.mpluess.mandelbrotexplorer;

import java.io.File;
import java.io.IOException;
import java.util.Stack;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javafx.application.Application;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.EventHandler;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;

import javax.imageio.ImageIO;

public class MandelbrotExplorer extends Application {
	/////////
	// Config
	
	// Display
	
	// Window size (square)
	private static final int WINDOW_WIDTH = 960;
	
	// Image size (square, decides over the computing time needed)
	// Image is then scaled down to window size.
	// 5000 makes for a real nice image.
	// 3000 is comparable in dense areas, clearly worse in sparse areas.
	private static final int WIDTH = 4800;
	
	private static final boolean INVERT_COLORS = false;

	// Program mode
	private enum ProgramMode {
		EXPLORER("Explorer"), SLIDE_SHOW("Slide Show");
		
		private final String name;
		
		private ProgramMode(String name) {
			this.name = name;
		}
		
		@Override
		public String toString() {
			return name;
		}
	}
	private static final ProgramMode PROGRAM_MODE = ProgramMode.EXPLORER;
	
	// Slide show params
//	private static final double SLIDE_SHOW_MIN_X = -2;
//	private static final double SLIDE_SHOW_MAX_X = 1;
//	private static final double SLIDE_SHOW_MIN_Y = -1.5;
//	private static final double SLIDE_SHOW_MAX_Y = 1.5;
	
	private static final double SLIDE_SHOW_MIN_X = -0.7346060000000001;
	private static final double SLIDE_SHOW_MAX_X = -0.72335;
	private static final double SLIDE_SHOW_MIN_Y = -0.18867900000000007;
	private static final double SLIDE_SHOW_MAX_Y = -0.17742300000000005;
	
	private static final double SLIDE_SHOW_MIN_STEP = 1 / Math.pow(10, 15);
	private static final double SLIDE_SHOW_MAX_STEP = (SLIDE_SHOW_MAX_X - SLIDE_SHOW_MIN_X) / WIDTH;
	
	private static final int SLIDE_SHOW_INTERVAL_MS = 15000;
	private static final boolean SLIDE_SHOW_SMALLER_STEPS = false;
	private static final int SLIDE_SHOW_SMALLER_STEPS_POWER = 4;
	
	// Algorithm parameters
	private static final int MAX_N = 500;
	private static final int MAX_VALUE_SQUARE = 4;
	
	// Threading
	private static final int THREADS = 8;
	private static final int TIMEOUT_SECONDS = 60;
	
	////////
	// State
	
	// Initial / currently active complex number range
	// Not using the MandelbrotState class here for performance reasons.
	
	// Standard Mandelbrot range
//	private double minX = -2;
//	private double maxX = 1;
//	private double minY = -1.5;
//	private double maxY = 1.5;
	
	private double minX = -2;
	private double maxX = 2;
	private double minY = -2;
	private double maxY = 2;
	
	// Try this, crazy stuff. Invert it.
	// chaos valley
	// tal vo trina und pluess
//	private double minX = -1.2535750159999999;
//	private double maxX = -1.2533004799999998;
//	private double minY = -0.021433680000000017;
//	private double maxY = -0.02115914400000002;
	
	// "Tal der Seepferdchen"
//	private double minX = -1;
//	private double maxX = 0;
//	private double minY = -0.5;
//	private double maxY = 0.5;
	
	// Test image inside "Tal der Seepferdchen"
//	private double minX = -0.7435069999999999;
//	private double maxX = -0.726671;
//	private double minY = -0.17215799999999995;
//	private double maxY = -0.15532199999999993;
	
	private double step;
	
	private volatile int[][] image;
	
	private static final int mandelbrotColor;
	private static final int nonMandelbrotColor;
	static {
		// black = 0, white = 1
		if (INVERT_COLORS) {
			mandelbrotColor = 1;
			nonMandelbrotColor = 0;
		}
		else {
			mandelbrotColor = 0;
			nonMandelbrotColor = 1;
		}
	}
	
	// Explorer
	private Rectangle selection;
	private boolean selectionStarted = false;
	private Stack<MandelbrotState> history = new Stack<MandelbrotState>();
	
	public static void main(String[] args) {
		launch(args);
	}

	@Override
	public void start(Stage primaryStage) {
		// init
		if (PROGRAM_MODE.equals(ProgramMode.SLIDE_SHOW)) {
			minX = SLIDE_SHOW_MIN_X;
			maxX = SLIDE_SHOW_MAX_X;
			minY = SLIDE_SHOW_MIN_Y;
			maxY = SLIDE_SHOW_MAX_Y;
		}
		updateSteps();
		
		// JavaFX init
		Canvas canvas = new Canvas(WINDOW_WIDTH, WINDOW_WIDTH);
		GraphicsContext gc = canvas.getGraphicsContext2D();
		drawAndCalculateImageWrapper(gc);
		
		Group root = new Group();
		root.getChildren().add(canvas);
		
		if (PROGRAM_MODE.equals(ProgramMode.EXPLORER)) {
			Pane glassPane = new Pane();
			// opacity = 0.0 --> transparent "glass pane"
			glassPane.setStyle("-fx-background-color: rgba(255, 255, 255, 0.0);");
			glassPane.setMaxWidth(WINDOW_WIDTH);
			glassPane.setMaxHeight(WINDOW_WIDTH);
			selection = new Rectangle();
			selection.setFill(Color.TRANSPARENT);
			selection.setStroke(Color.GREY);
			
			root.getChildren().add(glassPane);
			
			canvas.setOnMousePressed(new EventHandler<MouseEvent>() {
				@Override
				public void handle(MouseEvent event) {
					if (event.getButton() == MouseButton.PRIMARY) {
						selectionStarted = true;
						selection.setX(event.getSceneX());
						selection.setY(event.getSceneY());
						glassPane.getChildren().add(selection);
					}
				}
			});
			canvas.setOnMouseDragged(new EventHandler<MouseEvent>() {
				@Override
				public void handle(MouseEvent event) {
					if (selectionStarted) {
						double dx = event.getSceneX() - selection.getX();
						if (dx < 0) {
							selection.setTranslateX(dx);
							selection.setWidth(-dx);
						}
						else {
							selection.setTranslateX(0);
							selection.setWidth(dx);
						}
						
						double dy = event.getSceneY() - selection.getY();
						if (dy < 0) {
							selection.setTranslateY(dy);
							selection.setHeight(-dy);
						}
						else {
							selection.setTranslateY(0);
							selection.setHeight(dy);
						}
					}
				}
			});
			canvas.setOnMouseReleased(new EventHandler<MouseEvent>() {
				@Override
				public void handle(MouseEvent event) {
					if (event.getButton() == MouseButton.PRIMARY) {
						selectionStarted = false;
						glassPane.getChildren().remove(selection);
						
						// + 1 because the coordinates start from 0, not 1
						double selectionX = selection.getX() + selection.getTranslateX() + 1;
						double selectionY = selection.getY() + selection.getTranslateY() + 1;
						double selectionWidth = selection.getWidth();
						double selectionHeight = selection.getHeight();
						
						// this makes sure the old rectangle doesn't pop up on the next selection
						selection.setWidth(0);
						selection.setHeight(0);
						
						history.push(new MandelbrotState(minX, maxX, minY, maxY, step));
						
						// transform the selection from a rectangle to a square, take the shorter side
						if (selectionWidth < selectionHeight) {
							selectionHeight = selectionWidth;
						}
						else {
							selectionWidth = selectionHeight;
						}
						
						// adjust complex number range to the selection
						double xRange = maxX - minX;
						double yRange = maxY - minY;
						minX += (selectionX) / WINDOW_WIDTH * xRange;
						minY += (selectionY) / WINDOW_WIDTH * yRange;
						maxX -= (WINDOW_WIDTH - (selectionX + selectionWidth)) / WINDOW_WIDTH * xRange;
						maxY -= (WINDOW_WIDTH - (selectionY + selectionHeight)) / WINDOW_WIDTH * yRange;
						updateSteps();
	
						drawAndCalculateImageWrapper(gc);
					}
				}
			});
			canvas.setOnMouseClicked(new EventHandler<MouseEvent>() {
				@Override
				public void handle(MouseEvent event) {
					// go back to last image
					if (event.getButton() == MouseButton.SECONDARY) {
						if (!history.empty()) {
							MandelbrotState state = history.pop();
							minX = state.minX;
							maxX = state.maxX;
							minY = state.minY;
							maxY = state.maxY;
							step = state.step;
							
							drawAndCalculateImageWrapper(gc);
						}
					}
				}
			});
		}
		
		Scene scene = new Scene(root);
		scene.setOnKeyTyped(new EventHandler<KeyEvent>() {
            public void handle(KeyEvent event) {
            	// screenshot
            	// TODO save full resolution
            	if (event.getCharacter().equalsIgnoreCase("s")) {
            		WritableImage img = new WritableImage(WINDOW_WIDTH, WINDOW_WIDTH);
					canvas.snapshot(null, img);
					File file = new File("screenshots/" + System.currentTimeMillis() + ".png");
					try {
						ImageIO.write(SwingFXUtils.fromFXImage(img, null), "png", file);
						System.out.println("Screenshot saved to file " + file.getAbsolutePath());
					} catch (IOException e) {
						e.printStackTrace();
					}
            	}
            }
		});
		
		primaryStage.setTitle("Mandelbrot " + PROGRAM_MODE);
		primaryStage.setScene(scene);
		primaryStage.show();
		
		if (PROGRAM_MODE.equals(ProgramMode.SLIDE_SHOW)) {
			// Unfortunately, the delay is based on the start time of the
			// last TimerTask, not on its end time.
			new Timer().schedule(new TimerTask() {
			    @Override
			    public void run() {
					generateRandomState();
					drawAndCalculateImageWrapper(gc);
			    }
			}, 5000, SLIDE_SHOW_INTERVAL_MS);
		}
	}
	
	private void updateSteps() {
		step = (maxX - minX) / WIDTH;
	}

	private Image calculateImageWrapper() {
		System.out.println("[" + System.currentTimeMillis()
				+ "] Creating image for parameters minX=[" + minX + "], maxX=["
				+ maxX + "], minY=[" + minY + "], maxY=[" + maxY + "], step=["
				+ step + "]");
		long start = System.currentTimeMillis();
		image = new int[WIDTH][WIDTH];
		Image img = calculateImage();
		System.out.println("Image created in " + (System.currentTimeMillis() - start) + "ms.");
		return img;
	}
	
	private void drawAndCalculateImageWrapper(GraphicsContext gc) {
		long start = System.currentTimeMillis();
		gc.drawImage(calculateImageWrapper(), 0, 0);
		System.out.println("Image created and drawn in "
				+ (System.currentTimeMillis() - start) + "ms.");
	}
	
	private Image calculateImage() {
		assert WIDTH % WINDOW_WIDTH == 0;
		
		long start = System.currentTimeMillis();
		ExecutorService executorService = Executors.newFixedThreadPool(THREADS);
		for (int i = 0; i < THREADS; i++) {
			final int threadNumber = i;
			executorService.execute(new Runnable() {
			    public void run() {
			    	double cx = minX + threadNumber * step;
					for (int x = threadNumber; x < WIDTH; x += THREADS) {
						double cy = minY;
						for (int y = 0; y < WIDTH; y++) {
							// Bounded = member of the Mandelbrot set
							if (isMemberOfMandelbrotSet(cx, cy)) {
								image[x][y] = mandelbrotColor;
							} else {
								image[x][y] = nonMandelbrotColor;
							}
							cy += step;
						}
						cx += THREADS * step;
					}
			    }
			    
			    private boolean isMemberOfMandelbrotSet(double cx, double cy) {
					double zx = 0;
					double zy = 0;
					int n = 0;
					while ((zx * zx + zy * zy) < MAX_VALUE_SQUARE && n < MAX_N) {
						double oldZx = zx;

						// z * z + c
						zx = zx * zx - zy * zy + cx;
						zy = 2 * oldZx * zy + cy;

						++n;
					}
					return n == MAX_N;
				}
			});
		}
		
		executorService.shutdown();
		try {
			if (!executorService.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
				throw new RuntimeException("Calculation went on for too long, exiting.");
			}
		}
		catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
		
		System.out.println("Image calculated in "
				+ (System.currentTimeMillis() - start) + "ms.");
		
		int[][] finalImage = interpolate();
		System.out.println("Image calculated and interpolated in "
				+ (System.currentTimeMillis() - start) + "ms.");
		
		WritableImage image = toWritableImage(finalImage);
		System.out.println("Image calculated, interpolated and converted in "
				+ (System.currentTimeMillis() - start) + "ms.");
		
		return image;
	}
	
	// Scale image down from WIDTH * WIDTH to WINDOW_WIDTH * WINDOW_WIDTH.
	// This smooths out the image considerably by using gray tones as colors.
	// Example: width = 5000, windowWidth = 1000
	// Approximate runtime: 50ms
	// Pixel 0 / 0 is calculated the following way:
	// Go through Pixels [0-4] / [0-4], calculate the average color (gray) from the black and white colors.
	// Paint Pixel 0 / 0 with this calculated shade of gray.
	private int[][] interpolate() {
		int scaleFactor = WIDTH / WINDOW_WIDTH;
		int scaleFactorSquare = scaleFactor * scaleFactor;
		int[][] finalImage = new int[WINDOW_WIDTH][WINDOW_WIDTH];
		for (int x = 0; x < WINDOW_WIDTH; x++) {
			for (int y = 0; y < WINDOW_WIDTH; y++) {
				int colorSum = 0;
				for (int xImage = x * scaleFactor; xImage < (x * scaleFactor + scaleFactor); xImage++) {
					for (int yImage = y * scaleFactor; yImage < (y * scaleFactor + scaleFactor); yImage++) {
						colorSum += image[xImage][yImage];
					}
				}
				int colorRgb = (int)(((double)colorSum) / scaleFactorSquare * 255);
				finalImage[x][y] = colorRgb;
			}
		}
		return finalImage;
	}
	
	private WritableImage toWritableImage(int[][] image) {
		WritableImage img = new WritableImage(WINDOW_WIDTH, WINDOW_WIDTH);
		PixelWriter writer = img.getPixelWriter();
		for (int x = 0; x < WINDOW_WIDTH; x++) {
			for (int y = 0; y < WINDOW_WIDTH; y++) {
				int colorRgb = image[x][y];
				writer.setColor(x, y, Color.rgb(colorRgb, colorRgb, colorRgb));
			}
		}
		return img;
	}
	
	private void generateRandomState() {
		do {
			// get from a number in range 0..1 via 0..3 to -2..1
			minX = Math.random() * (SLIDE_SHOW_MAX_X - SLIDE_SHOW_MIN_X) + SLIDE_SHOW_MIN_X;
			// get from a number in range 0..1 via 0..3 to -1.5..1.5
			minY = Math.random() * (SLIDE_SHOW_MAX_Y - SLIDE_SHOW_MIN_Y) + SLIDE_SHOW_MIN_Y;
			// get from a number in range 0..1 to 10^-15..6*10^-4
			step = Math.random() * (SLIDE_SHOW_MAX_STEP - SLIDE_SHOW_MIN_STEP) + SLIDE_SHOW_MIN_STEP;
			// Smaller steps can be more interesting. Smaller chance to find something exciting though.
			if (SLIDE_SHOW_SMALLER_STEPS) {
				step /= Math.pow(10, Math.ceil(Math.random() * SLIDE_SHOW_SMALLER_STEPS_POWER));
			}
			
			maxX = minX + step;
			maxY = minY + step;
		}
		while (maxX > SLIDE_SHOW_MAX_X || maxY > SLIDE_SHOW_MAX_Y || step < SLIDE_SHOW_MIN_STEP);
	}
}
