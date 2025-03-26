from Cython.Build import cythonize
import sysconfig
from setuptools import setup, Extension
import numpy
from distutils.command.build_ext import build_ext
import os

include_dirs = [sysconfig.get_paths()["include"]]
library_dirs = [sysconfig.get_config_var("LIBDIR")]
include_dirs.append(numpy.get_include())

libraries = [sysconfig.get_config_var(
    "LDLIBRARY").replace("lib", "").replace(".so", "")]

print(library_dirs)

setup_dir = os.path.dirname(os.path.abspath(__file__))


class MySuffix(build_ext):
    suffix = '.so'

    def get_ext_filename(self, ext_name):
        return os.path.join(*ext_name.split('.')) + self.suffix


setup(
    ext_modules=cythonize(
        Extension(
            "libpythonWrapper",
            ["./libpythonWrapper.pyx", "./Helpers/c_helper.c"],
            include_dirs=include_dirs,
            library_dirs=library_dirs,
            libraries=libraries,
            extra_compile_args=["-w"],
            define_macros=[("PLUGIN_PATH", f'"{setup_dir}"')] 
        ),
        build_dir="build"
    ),

    cmdclass={'build_ext': MySuffix},

)
