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

public class MandelbrotExplorer extends Application {
	private MandelbrotConfig config;
	private MandelbrotState state;
	private volatile int[][] image;
	
	private static class MandelbrotConfig {
		// Window size (square)
		public final int windowWidth;
		
		// Image size (square, decides over the computing time needed)
		// Image is then scaled down to window size.
		public final int width;

		// Algorithm parameters
		public final int maxN;
		public final int maxValueSquare;
		
		public MandelbrotConfig() {
			windowWidth = 1000;
			
			// Comparable in dense areas, clearly worse in sparse areas
			//width = 3000;
			//Makes for a real nice image
			width = 5000;
			
			maxN = 500;
			maxValueSquare = 4;
		}

//		public MandelbrotConfig(int windowWidth, int width, int maxN,
//				int maxValueSquare) {
//			this.windowWidth = windowWidth;
//			this.width = width;
//			this.maxN = maxN;
//			this.maxValueSquare = maxValueSquare;
//		}
	}
	
	private static class MandelbrotState {
		// Initial complex number range
		public double sceneXPressed;
		public double sceneYPressed;
		
		// Standard Mandelbrot range
		public double minX = -2;
		public double maxX = 1;
		public double minY = -1.5;
		public double maxY = 1.5;
		
		// "Tal der Seepferdchen"
//		public double state.minY = -1;
//		public double maxX = 0;
//		public double minY = -0.5;
//		public double maxY = 0.5;
		
		// Test image inside "Tal der Seepferdchen"
//		public double minX = -0.7435069999999999;
//		public double maxX = -0.726671;
//		public double minY = -0.17215799999999995;
//		public double maxY = -0.15532199999999993;
	}
	
	public static void main(String[] args) throws IOException {
		launch(args);
	}

	@Override
	public void start(Stage primaryStage) {
		// Mandelbrot init
		config = new MandelbrotConfig();
		state = new MandelbrotState();
		
		// JavaFX init
		primaryStage.setTitle("Mandelbrot");
		Group root = new Group();
		
		Canvas canvas = new Canvas(config.windowWidth, config.windowWidth);
		GraphicsContext gc = canvas.getGraphicsContext2D();
		updateImage(gc);
		canvas.setOnMousePressed(new EventHandler<MouseEvent>() {
			@Override
			public void handle(MouseEvent event) {
				state.sceneXPressed = event.getSceneX();
				state.sceneYPressed = event.getSceneY();
			}
		});
		canvas.setOnMouseReleased(new EventHandler<MouseEvent>() {
			@Override
			public void handle(MouseEvent event) {
				double minXOrig = state.minX;
				double minYOrig = state.minY;
				double sceneXReleased = event.getSceneX();
				double sceneYReleased = event.getSceneY();
				
				state.minX += (state.sceneXPressed + 1) / config.windowWidth * (state.maxX - state.minX);
				state.minY += (state.sceneYPressed + 1) / config.windowWidth * (state.maxY - state.minY);
				
				// Adjust image to be square.
				// Adjust to the short side of the rectangle drawn by the user.
				if ((sceneXReleased - state.sceneXPressed) > (sceneYReleased - state.sceneYPressed)) {
					state.maxX -= (config.windowWidth - (sceneYReleased - state.sceneYPressed + state.sceneXPressed) + 1) / config.windowWidth * (state.maxX - minXOrig);
					state.maxY -= (config.windowWidth - sceneYReleased + 1) / config.windowWidth * (state.maxY - minYOrig);
				}
				else if ((sceneXReleased - state.sceneXPressed) < (sceneYReleased - state.sceneYPressed)) {
					state.maxX -= (config.windowWidth - sceneXReleased + 1) / config.windowWidth * (state.maxX - minXOrig);
					state.maxY -= (config.windowWidth - (sceneXReleased - state.sceneXPressed + state.sceneYPressed) + 1) / config.windowWidth * (state.maxY - minYOrig);
				}
				else {
					state.maxX -= (config.windowWidth - sceneXReleased + 1) / config.windowWidth * (state.maxX - minXOrig);
					state.maxY -= (config.windowWidth - sceneYReleased + 1) / config.windowWidth * (state.maxY - minYOrig);
				}
				updateImage(gc);
			}
		});
		
		root.getChildren().add(canvas);
		primaryStage.setScene(new Scene(root));
		primaryStage.show();
	}

	private void updateImage(GraphicsContext gc) {
		System.out.println("Creating image for parameters minX=[" + state.minX
				+ "], maxX=[" + state.maxX + "], minY=[" + state.minY + "], maxY=[" + state.maxY
				+ "], stepX=[" + getStepX() + "], stepY=[" + getStepY() + "]");
		long start = System.currentTimeMillis();
		image = new int[config.width][config.width];
		createImage(gc);
		System.out.println("Image created in " + (System.currentTimeMillis() - start) + "ms.");
	}
	
	private void createImage(GraphicsContext gc) {
		assert config.width % config.windowWidth == 0;
		
		double cx = state.minX;
		for (int x = 0; x < config.width; x++) {
			double cy = state.minY;
			for (int y = 0; y < config.width; y++) {
				// Bounded = member of the Mandelbrot set
				if (!isBoundless(cx, cy)) {
					// black
					image[x][y] = 0;
				} else {
					// white
					image[x][y] = 1;
				}
				cy += getStepY();
			}
			cx += getStepX();
		}
		
		// Grayscale
		// Example: config.width = 5000, config.windowWidth = 1000
		// Pixel 0 / 0 is calculated the following way:
		// Go through Pixels [0-4] / [0-4], calculate the average color (gray) from the black and white colors.
		// Paint Pixel 0 / 0 with this calculated shade of gray.
		int scaleFactor = config.width / config.windowWidth;
		int scaleFactorSquare = scaleFactor * scaleFactor;
		for (int x = 0; x < config.windowWidth; x++) {
			for (int y = 0; y < config.windowWidth; y++) {
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

	private boolean isBoundless(double cx, double cy) {
		double zx = 0;
		double zy = 0;
		int n = 0;
		while ((zx * zx + zy * zy) < config.maxValueSquare && n < config.maxN) {
			double oldZx = zx;

			// z * z + c
			zx = zx * zx - zy * zy + cx;
			zy = 2 * oldZx * zy + cy;

			++n;
		}
		return n != config.maxN;
	}

	private double getStepX() {
		return (state.maxX - state.minX) / config.width;
	}
	
	private double getStepY() {
		return (state.maxY - state.minY) / config.width;
	}
	
}
