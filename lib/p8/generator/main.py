#!/usr/bin/env python
import configparser
import os
from jinja2 import Template
import re
import sys

def convert_to_list_when_apply(v):
    if v.startswith("#"):
        return [x.strip() for x in v[1:].split("|")]
    return v

def camel_to_snake(name):
    name = re.sub("(.)([A-Z][a-z]+)", r"\1_\2", name)
    return re.sub("([a-z0-9])([A-Z])", r"\1_\2", name).lower()

def mkString(*args):
    return "".join(args)

def is_valid_utf8(content):
    try:
        content.decode('utf-8')
        return True
    except UnicodeDecodeError:
        return False

def main():

    path = os.getenv("TEMPLATE_PATH")
    destination = os.getenv("DESTINATION_PATH")

    config = configparser.ConfigParser()
    config_path = os.path.join(path, ".default.ini")
    config.read(config_path)
    support_data = config["DOMAIN"]
    support_data = dict(support_data)
    support_data = {k: convert_to_list_when_apply(v) for k, v in support_data.items()}

    prefix = "P8_PARAM_"
    all=[(name[len(prefix):], value) for name, value in os.environ.items() if name.startswith(prefix)]
    all=[(k, convert_to_list_when_apply(v)) for k, v in all]
    data=dict(all)

    for root, dirs, files in os.walk(path):
        nroot = root[len(path) : ]
        ndir = os.path.join(destination, nroot)
        template = Template(ndir)
        ndir = template.render(data)
        if not os.path.isdir(ndir):
            os.makedirs(ndir)
        for f in files:
            if f == ".selector.ini":
                config = configparser.ConfigParser()
                config_path = os.path.join(root, f)
                config.read(config_path)
                dd = config["DEFAULT"]
                dd = dict(dd)
                dirActives = set(data[dd["value"]])
                allDir = set(support_data[dd["value"]])
                for delDir in allDir - dirActives:
                    dirs.remove(delDir)

            if f != ".default.ini" and f != ".selector.ini":
                with open(os.path.join(root, f), mode="rb") as bfile:
                    content = bfile.read()
                    if is_valid_utf8(content):
                        template = Template(open(os.path.join(root, f)).read())
                        template_file_name = Template(f)
                        f = template_file_name.render(data)
                        open(os.path.join(ndir, f), "w").write(template.render(data))
                    else:
                        open(os.path.join(ndir, f), "wb").write(content)


if __name__ == '__main__':
    main()
