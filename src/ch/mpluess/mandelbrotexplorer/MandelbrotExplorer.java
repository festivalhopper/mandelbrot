/**
 * @author Michel Pluess
 * 
 * Usage:
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
import java.util.EmptyStackException;
import java.util.Stack;
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
	
	// Window size (square)
	private final int WINDOW_WIDTH = 1000;
	
	// Image size (square, decides over the computing time needed)
	// Image is then scaled down to window size.
	// 5000 makes for a real nice image.
	// 3000 is comparable in dense areas, clearly worse in sparse areas.
	private final int WIDTH = 5000;

	// Algorithm parameters
	private final int MAX_N = 500;
	private final int MAX_VALUE_SQUARE = 4;
	
	// Threading
	private final int THREADS = 8;
	private final int TIMEOUT_SECONDS = 60;
	
	////////
	// State
	
	private double sceneXPressed;
	private double sceneYPressed;
	private boolean selectionStarted = false;
	
	// Initial complex number range
	
	// Standard Mandelbrot range
	private double minX = -2;
	private double maxX = 1;
	private double minY = -1.5;
	private double maxY = 1.5;
	
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
	
	private double stepX;
	private double stepY;
	
	private Stack<MandelbrotState> history = new Stack<MandelbrotState>();
	
	private volatile int[][] image;
	
	public static void main(String[] args) {
		launch(args);
	}

	@Override
	public void start(Stage primaryStage) {
		// init
		updateSteps();
		
		// JavaFX init
		primaryStage.setTitle("Mandelbrot");
		Group root = new Group();
		
		Canvas canvas = new Canvas(WINDOW_WIDTH, WINDOW_WIDTH);
		GraphicsContext gc = canvas.getGraphicsContext2D();
		updateImage(gc);
		
		Pane glassPane = new Pane();
		// opacity = 0.0 --> transparent "glass pane"
		glassPane.setStyle("-fx-background-color: rgba(255, 255, 255, 0.0);");
		glassPane.setMaxWidth(WINDOW_WIDTH);
		glassPane.setMaxHeight(WINDOW_WIDTH);
		Rectangle selection = new Rectangle();
		selection.setFill(Color.TRANSPARENT);
		selection.setStroke(Color.GREY);
		
		canvas.setOnMousePressed(new EventHandler<MouseEvent>() {
			@Override
			public void handle(MouseEvent event) {
				if (event.getButton() == MouseButton.PRIMARY) {
					sceneXPressed = event.getSceneX();
					sceneYPressed = event.getSceneY();
					selectionStarted = true;
					selection.setX(sceneXPressed);
					selection.setY(sceneYPressed);
					glassPane.getChildren().add(selection);
				}
			}
		});
		canvas.setOnMouseDragged(new EventHandler<MouseEvent>() {
			@Override
			public void handle(MouseEvent event) {
				if (selectionStarted) {
					selection.setWidth(event.getSceneX() - sceneXPressed);
					selection.setHeight(event.getSceneY() - sceneYPressed);
				}
			}
		});
		canvas.setOnMouseReleased(new EventHandler<MouseEvent>() {
			@Override
			public void handle(MouseEvent event) {
				if (event.getButton() == MouseButton.PRIMARY) {
					selectionStarted = false;
					glassPane.getChildren().remove(selection);
					selection.setWidth(0);
					selection.setHeight(0);
					history.push(new MandelbrotState(minX, maxX, minY, maxY, stepX, stepY));
					
					double minXOrig = minX;
					double minYOrig = minY;
					double sceneXReleased = event.getSceneX();
					double sceneYReleased = event.getSceneY();
					
					minX += (sceneXPressed + 1) / WINDOW_WIDTH * (maxX - minX);
					minY += (sceneYPressed + 1) / WINDOW_WIDTH * (maxY - minY);
					
					// Adjust image to be square.
					// Adjust to the short side of the rectangle drawn by the user.
					if ((sceneXReleased - sceneXPressed) > (sceneYReleased - sceneYPressed)) {
						maxX -= (WINDOW_WIDTH - (sceneYReleased - sceneYPressed + sceneXPressed) + 1) / WINDOW_WIDTH * (maxX - minXOrig);
						maxY -= (WINDOW_WIDTH - sceneYReleased + 1) / WINDOW_WIDTH * (maxY - minYOrig);
					}
					else if ((sceneXReleased - sceneXPressed) < (sceneYReleased - sceneYPressed)) {
						maxX -= (WINDOW_WIDTH - sceneXReleased + 1) / WINDOW_WIDTH * (maxX - minXOrig);
						maxY -= (WINDOW_WIDTH - (sceneXReleased - sceneXPressed + sceneYPressed) + 1) / WINDOW_WIDTH * (maxY - minYOrig);
					}
					else {
						maxX -= (WINDOW_WIDTH - sceneXReleased + 1) / WINDOW_WIDTH * (maxX - minXOrig);
						maxY -= (WINDOW_WIDTH - sceneYReleased + 1) / WINDOW_WIDTH * (maxY - minYOrig);
					}
					
					updateSteps();
					updateImage(gc);
				}
			}
		});
		canvas.setOnMouseClicked(new EventHandler<MouseEvent>() {
			@Override
			public void handle(MouseEvent event) {
				// go back to last image
				if (event.getButton() == MouseButton.SECONDARY) {
					try {
						MandelbrotState state = history.pop();
						minX = state.minX;
						maxX = state.maxX;
						minY = state.minY;
						maxY = state.maxY;
						stepX = state.stepX;
						stepY = state.stepY;
						updateImage(gc);
					}
					catch (EmptyStackException e) {
						return;
					}
				}
			}
		});
		
		root.getChildren().addAll(canvas, glassPane);
		
		Scene scene = new Scene(root);
		scene.setOnKeyTyped(new EventHandler<KeyEvent>() {
            public void handle(KeyEvent event) {
            	// screenshot
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
		
		primaryStage.setScene(scene);
		primaryStage.show();
	}
	
	private void updateSteps() {
		stepX = (maxX - minX) / WIDTH;
		stepY = (maxY - minY) / WIDTH;
	}

	private void updateImage(GraphicsContext gc) {
		System.out.println("Creating image for parameters minX=[" + minX
				+ "], maxX=[" + maxX + "], minY=[" + minY + "], maxY=[" + maxY
				+ "], stepX=[" + stepX + "], stepY=[" + stepY + "]");
		long start = System.currentTimeMillis();
		image = new int[WIDTH][WIDTH];
		Image img = calculateImage();
		gc.drawImage(img, 0, 0);
		System.out.println("Image created in " + (System.currentTimeMillis() - start) + "ms.");
	}
	
	private Image calculateImage() {
		assert WIDTH % WINDOW_WIDTH == 0;
		
		ExecutorService executorService = Executors.newFixedThreadPool(THREADS);
		for (int i = 0; i < THREADS; i++) {
			final int threadNumber = i;
			executorService.execute(new Runnable() {
			    public void run() {
			    	double cx = minX + threadNumber * stepX;
					for (int x = threadNumber; x < WIDTH; x += THREADS) {
						double cy = minY;
						for (int y = 0; y < WIDTH; y++) {
							// Bounded = member of the Mandelbrot set
							if (!isBoundless(cx, cy)) {
								// black
								image[x][y] = 0;
							} else {
								// white
								image[x][y] = 1;
							}
							cy += stepY;
						}
						cx += THREADS * stepX;
					}
			    }
			    
			    private boolean isBoundless(double cx, double cy) {
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
					return n != MAX_N;
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
		
		// Grayscale
		// Approximate runtime for width = 5000, windowWidth = 1000: 50ms
		// Example: width = 5000, windowWidth = 1000
		// Pixel 0 / 0 is calculated the following way:
		// Go through Pixels [0-4] / [0-4], calculate the average color (gray) from the black and white colors.
		// Paint Pixel 0 / 0 with this calculated shade of gray.
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
		
		WritableImage img = new WritableImage(WINDOW_WIDTH, WINDOW_WIDTH);
		PixelWriter writer = img.getPixelWriter();
		for (int x = 0; x < WINDOW_WIDTH; x++) {
			for (int y = 0; y < WINDOW_WIDTH; y++) {
				int colorRgb = finalImage[x][y];
				writer.setColor(x, y, Color.rgb(colorRgb, colorRgb, colorRgb));
			}
		}
		
		return img;
	}
}
