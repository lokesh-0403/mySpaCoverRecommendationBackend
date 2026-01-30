package zasyaSolutions.mySpaCoverSkuRecommendation.model;

public class SpaCoverDimension {

	  private int dimensionA;
	    private int dimensionB;
	    private int dimensionC;
	  

	    public SpaCoverDimension(int dimensionA, int dimensionB, int dimensionC) {
	        this.dimensionA = dimensionA;
	        this.dimensionB = dimensionB;
	        this.dimensionC = dimensionC;
	    }

	   

	    // Getters and Setters
	    public int getDimensionA() {
	        return dimensionA;
	    }

	    public void setDimensionA(int dimensionA) {
	        this.dimensionA = dimensionA;
	    }

	    public int getDimensionB() {
	        return dimensionB;
	    }

	    public void setDimensionB(int dimensionB) {
	        this.dimensionB = dimensionB;
	    }

	    public int getDimensionC() {
	        return dimensionC;
	    }

	    public void setDimensionC(int dimensionC) {
	        this.dimensionC = dimensionC;
	    }

	

	    @Override
	    public String toString() {
	        return "SpaCoverDimension{" +
	                "A=" + dimensionA +
	                ", B=" + dimensionB +
	                ", C=" + dimensionC +
	                '}';
	    }
}
