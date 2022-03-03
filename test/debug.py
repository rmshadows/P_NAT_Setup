#!/bin/python3

import shutil
import os.path as op

a = op.join(".", "a")
b = op.join(".", "b")

shutil.copytree(a, b)

