package ch.mpluess.mandelbrotexplorer;

import java.io.IOException;

import javafx.application.Application;
import javafx.event.EventHandler;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

//TODO Code refactoring
//TODO Enhance selection usability. Allow selection from all directions.
//TODO Make threading the right way.
//TODO algorithm optimizations (see Wikipedia)
//TODO Move left / right / up / down (e.g. by one page / image)
//TODO Colors
//TODO Choose random range for images, show slideshow.
//TODO Save calculated images.
//TODO Screenshot function
//TODO (Enhance floating point precision.)
//TODO (Matlab port)
public class MandelbrotExplorer extends Application {
	// Window size (square)
	private static final int WINDOW_WIDTH = 1000;
	
	// Image size (square, decides over the computing time needed)
	// Image is then scaled down to window size.
	
	// Comparable in dense areas, clearly worse in sparse areas
//	private static final int WIDTH = 3000;
	
	// Makes for a real nice image
	private static final int WIDTH = 5000;

	// Algorithm parameters
	private static final int MAX_N = 500;
	private static final int MAX_VALUE_SQUARE = 4;

	// Initial complex number range
	private static double sceneXPressed;
	private static double sceneYPressed;
	
	// Standard Mandelbrot range
	private static double minX = -2;
	private static double maxX = 1;
	private static double minY = -1.5;
	private static double maxY = 1.5;
	
	// "Tal der Seepferdchen"
//	private static double minX = -1;
//	private static double maxX = 0;
//	private static double minY = -0.5;
//	private static double maxY = 0.5;
	
	// Test image inside "Tal der Seepferdchen"
//	private static double minX = -0.7435069999999999;
//	private static double maxX = -0.726671;
//	private static double minY = -0.17215799999999995;
//	private static double maxY = -0.15532199999999993;
	
	public static void main(String[] args) throws IOException {
		launch(args);
	}

	@Override
	public void start(Stage primaryStage) {
		primaryStage.setTitle("Mandelbrot");
		Group root = new Group();
		
		Canvas canvas = new Canvas(WINDOW_WIDTH, WINDOW_WIDTH);
		GraphicsContext gc = canvas.getGraphicsContext2D();
		initializeImage(gc);
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
				initializeImage(gc);
			}
		});
		
		root.getChildren().add(canvas);
		primaryStage.setScene(new Scene(root));
		primaryStage.show();
	}

	private static void initializeImage(GraphicsContext gc) {
		System.out.println("Creating image for parameters minX=[" + minX
				+ "], maxX=[" + maxX + "], minY=[" + minY + "], maxY=[" + maxY
				+ "], stepX=[" + getStepX() + "], stepY=[" + getStepY() + "]");
		long start = System.currentTimeMillis();
		createImage(gc, minX, minY, getStepX(), getStepY());
		System.out.println("Image created in " + (System.currentTimeMillis() - start) + "ms.");
	}
	
	private static void createImage(GraphicsContext gc, double minX, double minY, double stepX, double stepY) {
		assert WIDTH % WINDOW_WIDTH == 0;
		
		double cx = minX;
		int[][] image = new int[WIDTH][WIDTH];
		for (int x = 0; x < WIDTH; x++) {
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
			cx += stepX;
		}
		
		// Grayscale
		// Example: WIDTH = 5000, WINDOW_WIDTH = 1000
		// Pixel 0 / 0 is calculated the following way:
		// Go through Pixels [0-4] / [0-4], calculate the average color (gray) from the black and white colors.
		// Paint Pixel 0 / 0 with this calculated shade of gray.
		int scaleFactor = WIDTH / WINDOW_WIDTH;
		int scaleFactorSquare = scaleFactor * scaleFactor;
		for (int x = 0; x < WINDOW_WIDTH; x++) {
			for (int y = 0; y < WINDOW_WIDTH; y++) {
				int colorSum = 0;
				for (int xImage = x * scaleFactor; xImage < (x * scaleFactor + scaleFactor); xImage++) {
					for (int yImage = y * scaleFactor; yImage < (y * scaleFactor + scaleFactor); yImage++) {
						colorSum += image[xImage][yImage];
					}
				}
				int colorRgb = (int)(((double)colorSum) / scaleFactorSquare * 255);
				gc.setFill(Color.rgb(colorRgb, colorRgb, colorRgb));
				gc.fillRect(x, y, 1, 1);
			}
		}
	}

	private static boolean isBoundless(double cx, double cy) {
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

	private static double getStepX() {
		return (maxX - minX) / WIDTH;
	}
	
	private static double getStepY() {
		return (maxY - minY) / WIDTH;
	}
	
}
