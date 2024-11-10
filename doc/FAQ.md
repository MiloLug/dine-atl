# FAQ

In this section we cover some frequently asked questions about ATL.

## why Gtk?

The right tool for the job would probably be some meta-toolkit, so that what we're doing with it would be an intended usecase. That being said, Gtk is a popular, mature, well-maintained project with a C interface (in fact, it's written in C), and above all we think having android apps be Gtk apps is cool and this cool factor could (we hope) attract contributors

## I don't care if this is cleaner than running an entire AOSP system in a container, are there any tangible benefits?

Our design allows for fast start-up time (there's also still room for improvement) and low RAM usage, and it also makes it easy to do stuff like make apps using OpenXR use the system openxrloader and therefore use a regular Linux build of Monado as the OpenXR implementation. Modding is also easier, as is debugging.
