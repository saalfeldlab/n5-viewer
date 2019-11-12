# N5 Viewer [![Build Status](https://travis-ci.org/saalfeldlab/n5-viewer.svg?branch=master)](https://travis-ci.org/saalfeldlab/n5-viewer)
BigDataViewer-based tool for browsing multichannel multiscale [N5](https://github.com/saalfeldlab/n5) datasets.

### Installation
The following packages are required for installation:
* Apache Maven - https://maven.apache.org/
* Oracle JDK / OpenJDK - https://www.oracle.com/technetwork/java/javase/downloads/index.html

To install the application as a *Fiji/ImageJ* plugin, run the following:
```bash
python install.py <path to Fiji.app>
```
Then, it will be available in *Fiji* under *Plugins* -> *BigDataViewer* -> *N5 Viewer*.

### Usage

#### Storage options
The plugin supports multiple storage options:
* Filesystem (local/network drives)
* Amazon Web Services S3
* Google Cloud Storage

Datasets stored on the filesystem are represented by the root N5 directory. For cloud storages, every N5 container is a bucket. You will be prompted to select an appropriate storage type and an N5 container as an input for the plugin.

#### Container structure
The plugin specifies the following N5 container structure:

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
`s0`, `s1`, `s2` are standard N5 datasets representing scale levels. `s0` corresponds to full resolution.

Each scale level dataset (except `s0`) should specify its downsampling factors in its `attributes.json` as follows:
```json
{
  "downsamplingFactors":[2,2,2]
}
```

Additionally, scale level dataset attributes can also specify the resolution of the data in the following format:
```json
{
  "pixelResolution":{"unit":"um","dimensions":[0.097,0.097,0.18]}
}
```

The above attributes format is fully compatible with scale pyramids generated with [N5 Spark](https://github.com/saalfeldlab/n5-spark).

DEPRECATED: alternatively, downsampling factors and pixel resolution can be stored in the root N5 attributes as `scales` and `pixelResolution`.

#### Authentication

Fetching data from cloud storages requires authentication. You will be prompted for your user credentials on first use of the respective cloud service. For Google Cloud, a browser tab will pop up with web-based authentication prompt. For AWS, the credentials need to be initialized with `aws configure`, more information is available [here](https://docs.aws.amazon.com/cli/latest/userguide/cli-chap-getting-started.html#cli-quick-configuration).

#### Viewer state

The changes made to the viewer state are saved automatically to `bdv-settings.xml` file in the root N5 container:
* brightness & contrast
* color scheme
* display mode (fused/single, interpolation)
* channel grouping
* channel transformations
* bookmarks

To prevent concurrent modification, exclusive file locking is enforced, and the application will warn and suggest to open in read-only mode if somebody is already browsing the dataset.

#### Cropping tool

The application has a built-in cropping tool for extracing parts of the dataset as a ImageJ image (can be converted to commonly supported formats such as TIFF series).

Place the mouse pointer at the desired center position of the extracted image and hit `SPACE`. The dialog will pop up where you can specify the dimensions of the extracted image.

The image is cropped <i>without</i> respect to the camera orientation, so the cropped image will always contain Z-slices.
