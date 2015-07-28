package ch.mpluess.mandelbrotexplorer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javafx.application.Application;
import javafx.event.EventHandler;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

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
	
	public double sceneXPressed;
	public double sceneYPressed;
	
	// Initial complex number range
	
	// Standard Mandelbrot range
	public double minX = -2;
	public double maxX = 1;
	public double minY = -1.5;
	public double maxY = 1.5;
	
	// "Tal der Seepferdchen"
//	public double minX = -1;
//	public double maxX = 0;
//	public double minY = -0.5;
//	public double maxY = 0.5;
	
	// Test image inside "Tal der Seepferdchen"
//	public double minX = -0.7435069999999999;
//	public double maxX = -0.726671;
//	public double minY = -0.17215799999999995;
//	public double maxY = -0.15532199999999993;
	
	public double stepX;
	public double stepY;
	
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
		canvas.setOnMousePressed(new EventHandler<MouseEvent>() {
			@Override
			public void handle(MouseEvent event) {
				sceneXPressed = event.getSceneX();
				sceneYPressed = event.getSceneY();
			}
		});
		canvas.setOnMouseReleased(new EventHandler<MouseEvent>() {
			@Override
			public void handle(MouseEvent event) {
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
		});
		
		root.getChildren().add(canvas);
		primaryStage.setScene(new Scene(root));
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
		createImage(gc);
		System.out.println("Image created in " + (System.currentTimeMillis() - start) + "ms.");
	}
	
	private void createImage(GraphicsContext gc) {
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
		
		//TODO scheint gar nicht schneller zu sein
		int[][] finalImage;
		if (isImageCached()) {
			System.out.println("Reading image from cache.");
			finalImage = readCachedImage();
		}
		else {
			// Grayscale
			// Example: width = 5000, windowWidth = 1000
			// Pixel 0 / 0 is calculated the following way:
			// Go through Pixels [0-4] / [0-4], calculate the average color (gray) from the black and white colors.
			// Paint Pixel 0 / 0 with this calculated shade of gray.
			int scaleFactor = WIDTH / WINDOW_WIDTH;
			int scaleFactorSquare = scaleFactor * scaleFactor;
			finalImage = new int[WINDOW_WIDTH][WINDOW_WIDTH];
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
		}
		
		for (int x = 0; x < WINDOW_WIDTH; x++) {
			for (int y = 0; y < WINDOW_WIDTH; y++) {
				int colorRgb = finalImage[x][y];
				gc.setFill(Color.rgb(colorRgb, colorRgb, colorRgb));
				gc.fillRect(x, y, 1, 1);
			}
		}
		
		cacheImage(finalImage);
	}
	
	private boolean isImageCached() {
		return new File(getCachedImagePath()).exists();
	}
	
	private boolean isImageCached(String path) {
		return new File(path).exists();
	}
	
	private void cacheImage(int[][] image) {
		String path = getCachedImagePath();
		if (!isImageCached(path)) {
			System.out.println("Caching image.");
			try {
				FileOutputStream fos = new FileOutputStream(path);
				ObjectOutputStream out = new ObjectOutputStream(fos);
				out.writeObject(image);
				out.flush();
				out.close();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
	}
	
	private int[][] readCachedImage() {
		FileInputStream fis;
		try {
			fis = new FileInputStream(getCachedImagePath());
			ObjectInputStream in = new ObjectInputStream(fis);
			return (int[][])in.readObject();
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		} catch (IOException e) {
			throw new RuntimeException(e);
		} catch (ClassNotFoundException e) {
			throw new RuntimeException(e);
		}
	}
	
	private String getCachedImagePath() {
		return "image_cache/" + WIDTH + "_" + minX + "_" + maxX + "_" + minY + "_" + maxY + ".dat";
	}
}
