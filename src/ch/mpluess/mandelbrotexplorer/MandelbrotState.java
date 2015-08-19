package ch.mpluess.mandelbrotexplorer;

public class MandelbrotState {
	public final double minX;
	public final double maxX;
	public final double minY;
	public final double maxY;
	public final double step;
	
	public MandelbrotState(double minX, double maxX, double minY,
			double maxY, double step) {
		this.minX = minX;
		this.maxX = maxX;
		this.minY = minY;
		this.maxY = maxY;
		this.step = step;
	}
}
