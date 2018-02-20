# ParallelCoords

This interactive parallel coordinates plot displays data on the populations of North American waterfowl grouped by state and by year. Mousing over a line will cause it to be selected, and a popup with the value of the nearest axis will be displayed. I chose not to display values for all axes, because the screen got too crowded. Users also have the ability to draw bounding boxes on the screen, selecting multiple points--in this case, values for all of the points inside the bounding box will be displayed. Users can flip axes by clicking the "Invert" button at the top. Finally, clicking on an axis will color all lines on the graph according to their values for that axis, which is useful for spotting correlations.

I attempted to host this graph in javascript form on GitHub pages; however I discovered that processing.js has not yet added support for table objects in Processing 3. Since tables are an integral part of my code, the page cannot be displayed.
