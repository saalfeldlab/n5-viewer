# N5 Viewer [![Build Status](https://github.com/saalfeldlab/n5-viewer/actions/workflows/build-main.yml/badge.svg)](https://github.com/saalfeldlab/n5-viewer/actions/workflows/build-main.yml)
BigDataViewer-based tool for visualizing [N5](https://github.com/saalfeldlab/n5) datasets.

### Usage

The plugin will be available in Fiji as *Plugins -> BigDataViewer -> HDF5/N5/Zarr/OME-NGFF Viewer*,
See also the [N5 Fiji plugin](https://github.com/saalfeldlab/n5-ij).

#### Storage options
The plugin supports multiple storage formats:
* HDF5 (local file only)
* N5 (local and cloud)
* Zarr (local and cloud)

and multiple storage backends:
* Filesystem
* Amazon Web Services S3
* Google Cloud Storage

#### Metadata and container 

The plugin allows to select which datasets to open from the container tree. While almost any dataset can be opened as a BigDataViewer source, the
plugin also tries to read the metadata from the storage format to determine the additional transformations, or treat a group as a multiscale source.
Supported metadata formats include:

* [OME-NGFF v0.4](https://ngff.openmicroscopy.org/0.4/)
* "N5 Viewer" described below.
* [COSEM](https://github.com/janelia-cellmap/schemas/blob/master/multiscale.md#contemporary-new-proposed-cosem-style-ie-elaborated-ome-zarr-spec)


#### "N5 Viewer" Metadata 

Early versions of this plugin developed the following naming and metadata specification we call the "N5 Viewer" metadata.
This plugin supports visualization of this format, and the [N5 Fiji plugins](https://github.com/saalfeldlab/n5-ij) support
reading and writing of this metadata format. 

```
└─ group
    ├─── s0 {} 
    ├─── s1 {"downsamplingFactors": [2, 2, 2]}
    ├─── s2 {"downsamplingFactors": [4, 4, 4]}
    ...
```

The downsampling factors values are given as an example and do not necessarily need to be powers of two.

Each scale level can also contain the pixel resolution attribute in one of the following forms:
```
{"pixelResolution": {"unit": "um", "dimensions": [0.097, 0.097, 0.18]}}
```
or
```
{"pixelResolution": [0.097, 0.097, 0.18]}
```
Both options are supported. For the second option with a plain array, the application assumes the unit to be `um`.

The above format is fully compatible with scale pyramids generated by [N5 Spark](https://github.com/saalfeldlab/n5-spark).

DEPRECATED: alternatively, downsampling factors and pixel resolution can be stored in the group N5 attributes as `scales` and `pixelResolution`.

#### Cloud storage access

Fetching data from a cloud storage may require authentication. If the bucket is not public, the application will try to find the user credentials on the local machine and use them to access the bucket.

The local user credentials can be initialized as follows:
* **Google Cloud**:
  Install [Google Cloud SDK](https://cloud.google.com/sdk/docs/) and run the following in the command line:
  ```
  gcloud auth application-default login
  ```
  This will open an OAuth 2.0 web prompt and then store the credentials on the machine.

* **Amazon Web Services**:
  Install [AWS Command Line Interface](https://aws.amazon.com/cli/) and run `aws configure` in the command line. You would need to enter your access key ID, secret key, and geographical region as described [here](https://docs.aws.amazon.com/cli/latest/userguide/cli-chap-getting-started.html#cli-quick-configuration).

#### Cropping tool

The application has a built-in cropping tool for extracing parts of the dataset as a ImageJ image (can be converted to commonly supported formats such as TIFF series).

Press `SPACE` or select the `Tools > Extract to ImageJ` menu options. A dialog will appear where you can specify the field of view and dimensions of the image you would like to extract.
The field of view can be edited by clicking and draggging corners of the displayed bounding box, moving the sliders in the new dialog, or manually editing their values.
If the image is multiscale, you can select which scale level to export using the drop down menu. If multiple channels or images are open, you can export either the current image only, 
or all visible images,  using the "Images to export" drop down.

The image is cropped <i>without</i> respect to the camera orientation, so slices of the cropped image will always be the `Z` dimension.
