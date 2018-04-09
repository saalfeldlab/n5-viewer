# n5-viewer
BigDataViewer-based tool for browsing multichannel multiscale [N5](https://github.com/saalfeldlab/n5) datasets.

### Installation
To install the application as a *Fiji/ImageJ* plugin, run the following:
```bash
mvn -Dimagej.app.directory=<path to Fiji> clean install
```
Then, it will be available in *Fiji* under *Plugins* -> *BigDataViewer* -> *N5 Viewer*.

### Usage
The application expects a path to a directory structured as follows:

```
/attributes.json

/c0/attributes.json
/c0/s0/
/c0/s1/
/c0/s2/
/c0/...

/c1/attributes.json
/c1/s0/
/c1/s1/
/c1/s2/
/c1/...

...
```

Root attributes are used as defaults for all channels. They can be overridden by setting channel-specific attributes.<br/>
`s0`, `s1`, `s2` are standard N5 datasets that represent scale levels for each channel. `s0` corresponds to full resolution.

Example of the `attributes.json` file:
```json
{
  "name":"some data",
  "scales":[[1,1,1],[2,2,1],[4,4,2],[8,8,4],[16,16,9],[32,32,17]],
  "pixelResolution":{"unit":"um","dimensions":[0.097,0.097,0.18]},
  "affineTransform":[[1,-0.30,-0.25,0],[0,1.25,0,0],[0,0,0.85,0]]
}
```

Alternatively, scales and pixel resolution can be stored and queried as attributes of individual scale level datasets. The attribute keys for individual values are `downsamplingFactors` and `pixelResolution`. Thus, root attributes or channel group attributes are not mandatory, which makes it fully compatible with scale pyramid exports generated with [N5 Spark](https://github.com/saalfeldlab/n5-spark).

### Viewer state

The changes made to the viewer state are saved automatically to `bdv-settings.xml` file in the root N5 directory:
* brightness & contrast
* color scheme
* display mode (fused/single, interpolation)
* channel grouping
* channel transformations
* bookmarks

To prevent concurrent modification, exclusive file locking is enforced, and the application will warn and suggest to open in read-only mode if somebody is already browsing the dataset.

### Cropping tool

The application has a built-in cropping tool for extracing parts of the dataset as a ImageJ image (can be converted to commonly supported formats such as TIFF series).

Place the mouse pointer at the desired center position of the extracted image and hit `SPACE`. The dialog will pop up where you can specify the dimensions of the extracted image.

The image is cropped <i>without</i> respect to the camera orientation in the viewer, so the cropped image will always contain Z-slices.
