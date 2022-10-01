import setuptools
import pathlib
import sys

from setuptools import find_packages

here = pathlib.Path(__file__).parent.resolve()

install_requires = (
    (here / "requirements/common.txt").read_text(encoding="utf-8").splitlines()
)

setuptools.setup(
    name="p8 generator tool",
    version="0.0.1",
    author="J. Albert Cruz Almaguer",
    author_email="jalbertcruz@gmail.com",
    license="MIT",
    package_dir={
        "": "lib",
    },
    packages=find_packages("lib"),
    install_requires=install_requires,
    scripts=[
        "bin/p8",
        "bin/diyp8",
    ],
    entry_points={
        "console_scripts": [
            "_p8=templates.generator:main",
        ],
    },
)
