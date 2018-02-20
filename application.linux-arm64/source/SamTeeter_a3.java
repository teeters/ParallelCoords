import processing.core.*; 
import processing.data.*; 
import processing.event.*; 
import processing.opengl.*; 

import java.util.HashMap; 

import java.util.HashMap; 
import java.util.ArrayList; 
import java.io.File; 
import java.io.BufferedReader; 
import java.io.PrintWriter; 
import java.io.InputStream; 
import java.io.OutputStream; 
import java.io.IOException; 

public class SamTeeter_a3 extends PApplet {



Table birds;
ParallelCoords pCoords;
float gX, gY, gW, gH;

public void setup(){
  //load data from csv
  birds = loadTable("bigbirdsums.csv", "header");
  //make sure all values numerical
  for (int col=0; col<birds.getColumnCount(); col++){
    if (birds.getColumnType(col) == Table.STRING){
      HashMap<String, Float> cValue = new HashMap<String, Float>();
      float v = 0.5f;
      for(int row=0; row<birds.getRowCount(); row++){
        String s = birds.getString(row, col);
        if(!cValue.containsKey(s)){
          cValue.put(s, v);
          v++;
        }
        birds.setFloat(row, col, cValue.get(s));
      }
    }
  }
  gX = 50;
  gY = 50;
  gW = width-gX*2;
  gH = height-gY*2;
  pCoords = new ParallelCoords(gX, gY, gW, gH, birds);
  
  surface.setResizable(true);
}

public void draw(){  
  background(0xffffff);
  if(gW != width-gX*2 || gH != height-gY*2){
    gW = width-gX*2;
    gH = height-gY*2;
    pCoords.updateScale(gX, gY, gW, gH);
  }
  pCoords.draw();
}

public void mouseClicked(){
  pCoords.handleClick();
}

public void mouseDragged(){
  pCoords.handleDrag();
}

public void mousePressed(){
  //pCoords needs to keep track of location for bounding box
  pCoords.dragStartX = mouseX;
  pCoords.dragStartY = mouseY;
}

public void mouseReleased(){
  pCoords.boundingBoxActive=false;
}

class ParallelCoords{
  float graphX, graphY, graphW, graphH;
  float xW, gradW;
  Table data;
  Table displayData; //store min, max, scale factor for each column
  Table rowDisplayData; //basically, keep track of which lines are selected
  int selectingAxis; //used for doing the animated transition to selecting an axis
  int selectedAxis;
  float selectL, selectR;
  int redColor, blueColor;
  PGraphics hoverBoxCanvas; //extra canvas for hover boxes, which go on top of everything
  PGraphics sL; //selected lines
  PGraphics bb; //bounding boxes
  float dragStartX, dragStartY; //keep track of where the mouse was dragged from to draw a bounding box
  boolean boundingBoxActive;
  
  ParallelCoords(float gX, float gY, float gW, float gH, Table d){
    graphX = gX;
    graphY = gY;
    graphW = gW;
    graphH = gH;
    data = d;
    gradW = 20;
    selectingAxis = selectedAxis = -1;
    boundingBoxActive = false;
    
    blueColor = color(65,105,225); //royal blue
    redColor = color(220,20,60); //crimson
    
    //calculate min, max for each column
    displayData = new Table();
    displayData.addColumn("minVal", Table.FLOAT);
    displayData.addColumn("maxVal", Table.FLOAT);
    displayData.addColumn("zeroY", Table.FLOAT);
    displayData.addColumn("scale", Table.FLOAT);
    displayData.addColumn("isFlipped", Table.INT);
    for(int i=0; i<data.getColumnCount(); i++){
      float[] col = data.getFloatColumn(i);
      float minVal, maxVal;
      TableRow newRow = displayData.addRow();
      minVal = min(col);
      maxVal = max(col);
      newRow.setFloat("minVal", minVal);
      newRow.setFloat("maxVal", maxVal);
      newRow.setInt("isFlipped",0);
    }
    
    rowDisplayData = new Table();
    rowDisplayData.addColumn("isSelected", Table.INT);
    for (int j=0; j<data.getRowCount(); j++){
      TableRow newRow = rowDisplayData.addRow();
      newRow.setInt("isSelected", 0);
    }
    
    //calculate other scaling factors
    updateScale(gX, gY, gW, gH);
  }
  
  public void updateScale(float gX, float gY, float gW, float gH){
    graphX = gX;
    graphY = gY;
    graphW = gW;
    graphH = gH;
    hoverBoxCanvas = createGraphics(width, height);
    sL = createGraphics(width, height);
    bb = createGraphics(width,height);

    
    for(int i=0; i<data.getColumnCount(); i++){
      float minVal = displayData.getFloat(i, "minVal");
      float maxVal = displayData.getFloat(i, "maxVal");
      float scale = gH/(maxVal-min(minVal,0));
      float zeroY = graphY + graphH + min(minVal, 0)*scale;
      displayData.setFloat(i, "scale", scale);
      displayData.setFloat(i, "zeroY", zeroY);
      println(data.getColumnTitle(i)+": "+str(getY(minVal, i))+", "+str(getY(maxVal,i)));
    }
    println("graphY+graphH: "+str(graphY+graphH));
    println("graphY: "+str(graphY));
    
    //screen-space separation between axes
    xW = graphW / (data.getColumnCount()-1);  
  }
  
  public void drawBackground(){
    //background should be a white rect with only top and bottom borders--axes will be on the sides
    noStroke();
    fill(0xffffff);
    rect(graphX, graphY, graphW, graphH);
    stroke(0,0,0);
    strokeWeight(1);
    line(graphX, graphY, graphX+graphW, graphY);
    line(graphX, graphY+graphH, graphX+graphW, graphY+graphH);
  }
  
  public int getColorForRow(int row, int selected){
    float alpha = 100;
    //give selected lines stronger alpha
      if (rowDisplayData.getInt(row, "isSelected")==1){
        alpha = 255;
      }
    if (selected !=-1){
      float colorVal = data.getFloat(row, selected)-displayData.getFloat(selected, "minVal");
      float cvRange = displayData.getFloat(selected, "maxVal")-displayData.getFloat(selected, "minVal");
      int b = withAlpha(blueColor, alpha);
      int r = withAlpha(redColor, alpha);
      return lerpColor(b, r, colorVal/cvRange);
    }
    return color(0,0,0, alpha);
  }
  
  public void drawLines(){
    //clear out selected lines
    sL.beginDraw();
    sL.clear();
    //update selected lines
    for(int row=0; row<data.getRowCount(); row++){
      //update line selection status
      if (rowIsSelected(row)){
        rowDisplayData.setInt(row, "isSelected", 1);
      } else {
        rowDisplayData.setInt(row, "isSelected", 0);
      }
    }
    
    //retrieve the appropriate color and location for each point on the line
    //and draw it. If it's in the region affected by the select animation, change the color accordingly
    //if it's on the boundary, draw two lines
    for(int col=1; col<data.getColumnCount(); col++){
      for (int row=0; row<data.getRowCount(); row++){
        //calculate line segment coords
        float x1 = graphX+xW*(col-1);
        float y1 = getY(data.getFloat(row, col-1), col-1);
        float x2 = graphX + xW*col;
        float y2 = getY(data.getFloat(row,col), col);
        int lineColor;
        
        //case 1: no animation in progress
        if (selectingAxis == -1){
          //color the line and draw it
          lineColor = getColorForRow(row, selectedAxis);
          drawSegment(row, lineColor, x1, y1, x2, y2);
        }
        else{
          //case 2a: the segment falls completely outside the animated region
          if (min(x1,x2) > selectR || max(x1,x2) < selectL){
            //color the line according to the selected axis
            lineColor = getColorForRow(row, selectedAxis);
            drawSegment(row, lineColor, x1, y1, x2, y2);
          }
          //case 2b: the segment is completely inside the animated region
          else if (min(x1,x2)>selectL && max(x1,x2)<selectR){
            //color the line the selecting color and draw it
            lineColor = getColorForRow(row, selectingAxis);
            drawSegment(row, lineColor, x1,y1,x2,y2);
          }
          //case 2c: the segment is partially to the left of the animated region
          else if (x1<=selectL && x2>=selectL){
            //draw two lines, one on each side of the boundary
            float slope = (y2-y1)/(x2-x1);
            float yInt = y2-slope*x2;
            lineColor = getColorForRow(row, selectedAxis);
            drawSegment(row, lineColor, x1, y1, selectL, selectL*slope+yInt);
            lineColor = getColorForRow(row, selectingAxis);
            drawSegment(row, lineColor, selectL, selectL*slope+yInt, x2, y2);
          }
          //case 2d: the segment is partially to the right
          else if (x1<=selectR && x2>=selectR){
            float slope = (y2-y1)/(x2-x1);
            float yInt = y2-slope*x2;
            lineColor = getColorForRow(row, selectingAxis);
            drawSegment(row, lineColor, x1, y1, selectR, selectR*slope+yInt);
            lineColor = getColorForRow(row, selectedAxis);
            drawSegment(row, lineColor, selectR, selectR*slope+yInt, x2, y2);
          }
          
        }
      }
    }
    //overlay selected lines
    sL.endDraw();
    image(sL, 0,0);
  }
  
  public void drawSegment(int row, int lineColor, float x1, float y1, float x2, float y2){
    if (rowDisplayData.getInt(row, "isSelected")==1){
      sL.stroke(lineColor);
      sL.strokeWeight(4);
      sL.line(x1,y1,x2,y2);
    } else {
      stroke(lineColor);
      strokeWeight(1);
      line(x1,y1,x2,y2);
    }
  }
  
  public void updateHoverBoxes(){
    //clear out hover boxes
    hoverBoxCanvas.beginDraw();
    hoverBoxCanvas.clear();
    for (int col=1; col<data.getColumnCount(); col++){
      for (int row=0; row<data.getRowCount(); row++){
        float x1 = graphX+xW*(col-1);
        float y1 = getY(data.getFloat(row, col-1), col-1);
        float x2 = graphX + xW*col;
        float y2 = getY(data.getFloat(row,col), col);
        
        //draw hoverbox if mouse is over this segment
        if(mouseOverLine(x1,y1,x2,y2)){
          int nearAxis = nearestMouse(col, col-1);
          int farAxis = furthestMouse(col, col-1);
          drawHoverBox(row, nearAxis, farAxis);
        } //or if the point is in a bounding box
        else if (lineInBoundingBox(x1, y1, x1+.1f, y1)){
          drawHoverBox(row, col-1, col);
        }
        //check the last column as well
        if (col==data.getColumnCount()-1){
          if (lineInBoundingBox(x2,y2,x2+.1f,y2)){
            drawHoverBox(row,col,col-1);
          }
        }
      }
    }
    hoverBoxCanvas.endDraw();
  }
  
  public int nearestMouse(int lCol, int rCol){
    //return the axis that the mouse is nearest to
    int nearAxis;
    if (abs(graphX+xW*lCol-mouseX) < abs(graphX+xW*rCol-mouseX)){
      nearAxis = lCol;
    } else {
      nearAxis = rCol;
    }
    return nearAxis;
  }
  
  public int furthestMouse(int lCol, int rCol){
    int farAxis;
    if (abs(graphX+xW*lCol-mouseX) < abs(graphX+xW*rCol-mouseX)){
      farAxis = rCol;
    } else {
      farAxis = lCol;
    }
    return farAxis;
  }
  
  public void drawHoverBox(int row, int nearAxis, int farAxis){
    //println("Nearaxis:", nearAxis, "Faraxis:", farAxis);
    float nearX = graphX+xW*nearAxis;
    float farX = graphX+xW*farAxis;
    float nearY = getYForIndex(row, nearAxis);
    float farY = getYForIndex(row, farAxis);
    float x=nearX, y=nearY;
    //iterate over all rows that have been rendered before this one
    //for each one of those rows with a similar value for this axis,
    //move the location of the box down the line towards farAxis
    for(int r=0; r<row; r++){
      if(rowDisplayData.getInt(r, "isSelected")==1 && abs(getYForIndex(r,nearAxis) - getYForIndex(row,nearAxis)) < 5){
        x = lerp(x, farX, 0.15f);
        y = lerp(y, farY, 0.15f);
      }
    }
    //figure out how big the text will be
    hoverBoxCanvas.textSize(10);
    String hoverText = str(data.getFloat(row, nearAxis));
    float w = hoverBoxCanvas.textWidth(hoverText);
    //draw the box
    hoverBoxCanvas.fill(255,255,255,255);
    hoverBoxCanvas.strokeWeight(1);
    hoverBoxCanvas.stroke(0,0,0);
    hoverBoxCanvas.rect(x,y,w+4,14);
    //draw the text
    hoverBoxCanvas.fill(0,0,0);
    hoverBoxCanvas.text(hoverText, x+2, y+12); 
  }
  
  public boolean rowIsSelected(int row){
    for(int col=1; col<data.getColumnCount(); col++){
        float x1 = graphX+xW*(col-1);
        float y1 = getY(data.getFloat(row, col-1), col-1);
        float x2 = graphX + xW*col;
        float y2 = getY(data.getFloat(row,col), col);
        if (mouseOverLine(x1,y1,x2,y2)){
          return true;
        }
        if (lineInBoundingBox(x1,y1,x2,y2)){
          return true;
        }
    }
    return false;
  }
  
  public void drawAxis(int axis){
    //In its default state, just a vertical line with a small tick for zero
    //If mouseover, display faded heatmap
    //If selected, display strong heatmap
    //Axis may be flipped
    float aX = graphX+axis*xW;
    boolean mouseOver = mouseOverAxis(axis);
    boolean isFlipped = displayData.getInt(axis, "isFlipped")==1;

    boolean isSelected=false;
    //if another axis is in the process of selecting, only that axis should have the gradient
    if(axis == selectingAxis){
      isSelected=true;
    } else if (selectingAxis==-1 && selectedAxis==axis){
      isSelected=true;
    }

    //first let's handle the selected or mouseOver cases
    if (isSelected || mouseOver){
      float alpha = 100;
      if(isSelected){
        alpha = 255;
      }
      int topColor, bottomColor;
      if (isFlipped){
        topColor = blueColor;
        bottomColor = redColor;
      } else{
        topColor = redColor;
        bottomColor = blueColor;
      }
      topColor = color(red(topColor), green(topColor), blue(topColor), alpha);
      bottomColor = color(red(bottomColor), green(bottomColor), blue(bottomColor), alpha);
      
      //draw heatmap ranging from blue (min) to red (max)
      for(float y = (graphY+graphH); y>graphY; y--){
        stroke(lerpColor(topColor, bottomColor, (y-graphY)/graphH));
        line(aX-.5f*gradW, y, aX+.5f*gradW, y);
      }
            
    }
    
    //now draw the axis line itself
    stroke(0,0,0);
    line(aX, graphY+graphH, aX, graphY);
    //and the zero tick
    line(aX-2.5f, getY(0,axis), aX+2.5f, getY(0,axis));
    //the name at the bottom
    textAlign(CENTER, TOP);
    fill(0,0,0);
    textSize(10);
    text(data.getColumnTitle(axis), aX-xW/2.0f, graphY+graphH+10, xW, 30);
    //the word "invert" at the top
    if(mouseOverInvert(axis)){
      textSize(12);
    } else {
      textSize(10);
    }
    text("Invert", aX, graphY-15);
  }
  
  public void doSelect(int axis){
    selectL = selectR = graphX+xW*axis;
    if(axis != selectedAxis){
      selectingAxis = axis;
    } else {
      selectedAxis = -1;
    }
  }
  
  public void doInvert(int axis){
    //if I have time I'll add an animation for this. Right now, just flip the axis
    if (displayData.getInt(axis, "isFlipped") == 1){
      displayData.setInt(axis, "isFlipped", 0);
    } else {
      displayData.setInt(axis, "isFlipped", 1);
    }
  }
  
  public boolean mouseOverAxis(int axis){
    float aX = graphX+axis*xW;
    return mouseInRect(aX-gradW/2, graphY, gradW, graphH);
  }
  
  public boolean mouseOverInvert(int axis){
    float aX = graphX + axis*xW;
    return mouseInRect(aX-8, graphY-15, 16, 15);
  }
  
  public boolean mouseOverLine(float x1, float y1, float x2, float y2){
    //calculate equation for line, see if mouse is on it
    float slope = (y2-y1)/(x2-x1);
    float yInt = y2 - slope*x2;
    if (mouseX >= x1 && mouseX <= x2 && abs(mouseX * slope + yInt - mouseY) < 2){
      return true;
    }
    return false;
  }
  
  public boolean lineInBoundingBox(float lx1, float ly1, float lx2, float ly2){
    if (!boundingBoxActive){
      return false;
    }
    //get line equation
    float slope = (ly2-ly1)/(lx2-lx1);
    float yInt = ly2-slope*lx2;
    //get coords for box walls
    float topY = max(mouseY, dragStartY);
    float bottomY = min(mouseY, dragStartY);
    float leftX = min(mouseX, dragStartX);
    float rightX = max (mouseX, dragStartX);
    //check if any line in bounding box is between ends of segment
    if(max(lx1,lx2)<leftX || min(lx1,lx2)>rightX || max(ly1,ly2)<bottomY || min(ly1,ly2)>topY){
      return false;
    }
    
    //check intersection with right wall
    float rightIntY = rightX*slope+yInt;
    if (rightIntY < topY && rightIntY > bottomY){
      return true;
    }
    //left wall
    float leftIntY = leftX*slope+yInt;
    if (leftIntY > bottomY && leftIntY < topY){
      return true;
    }
    //bottom
    float bottomIntX = (bottomY - yInt)/slope;
    if (bottomIntX > leftX && bottomIntX < rightX){
      return true;
    }
    //top
    float topIntX = (topY-yInt)/slope;
    if (topIntX > leftX && topIntX < rightX){
      return true;
    }
    
    return false;
  }
  
  public void handleClick(){
    //check if axis was selected
    for(int col=0; col<data.getColumnCount(); col++){
      if(mouseOverAxis(col)){
        doSelect(col);
      }
      
      if(mouseOverInvert(col)){
        doInvert(col);
      }
    }
  }
  
  public void handleDrag(){
    //draw the bounding box and let the rest of the program know it's there
    boundingBoxActive = true;
    bb.beginDraw();
    bb.clear();
    bb.stroke(0,0,0);
    bb.noFill();
    bb.strokeWeight(2);
    bb.rect(dragStartX, dragStartY, mouseX-dragStartX, mouseY-dragStartY);
    bb.endDraw();
  }
  
  public float getY(float val, int col){
    //returns the scaled y-coord for a point in the given column
    if (displayData.getInt(col, "isFlipped")==1){
      float zeroY = graphY+(graphY+graphH-displayData.getFloat(col, "zeroY"));
      return zeroY + val*displayData.getFloat(col, "scale");
    }
    return displayData.getFloat(col, "zeroY") - val*displayData.getFloat(col, "scale");
  }
  
  public float getYForIndex(int row, int col){
    return getY(data.getFloat(row, col), col);
  }
  
  public void draw(){
    //draw the basic graph
    drawBackground();
    for (int i=0; i<data.getColumnCount(); i++){
      drawAxis(i);
    }
    
    //if animation in progress, draw it and advance it
    float rate = 30;
    if(selectingAxis != -1){
      selectL = max(0, selectL-rate);
      selectR = min(graphX+graphW, selectR+rate);
      if (selectL == 0 && selectR == graphX+graphW){
        selectedAxis = selectingAxis;
        selectingAxis = -1;
      }
    }
    drawLines();
    
    //display bounding boxes if active
    if(boundingBoxActive){
      image(bb,0,0);
    }
    //display hoverboxes
    updateHoverBoxes();
    image(hoverBoxCanvas,0,0);
  }
  
}

public int withAlpha(int c, float alpha){
  return color(red(c), green(c), blue(c), alpha);
}

public boolean mouseInRect(float x, float y, float w, float h){
  return (mouseX>=x && mouseX<=x+w && mouseY>=y && mouseY<=y+h);
}
  public void settings() {  size(1000,500); }
  static public void main(String[] passedArgs) {
    String[] appletArgs = new String[] { "SamTeeter_a3" };
    if (passedArgs != null) {
      PApplet.main(concat(appletArgs, passedArgs));
    } else {
      PApplet.main(appletArgs);
    }
  }
}
