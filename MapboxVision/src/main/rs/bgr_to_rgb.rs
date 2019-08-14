#pragma version(1)
#pragma rs java_package_name(com.mapbox.vision.video.videosource.file)
#pragma rs_fp_relaxed

uchar4 RS_KERNEL bgrToRgb(uchar4 in, uint32_t x, uint32_t y)
{
    uchar4 out = in;
    out.r = in.b;
    out.b = in.r;
    return out;
}
