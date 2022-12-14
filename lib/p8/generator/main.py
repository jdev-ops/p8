#!/usr/bin/env python

import configparser
import os
from jinja2 import Template
import re


def camel_to_snake(name):
    name = re.sub("(.)([A-Z][a-z]+)", r"\1_\2", name)
    return re.sub("([a-z0-9])([A-Z])", r"\1_\2", name).lower()


def main():
    config = configparser.ConfigParser()
    path = os.getenv("TEMPLATE_PATH")
    destination = os.getenv("DESTINATION_PATH")
    config_path = f"{path}/default.ini"

    print(f"Reading configs from: {config_path}")
    config.read(config_path)

    data = config["DEFAULT"]
    data = dict(data)
    normal = {k: v for k, v in data.items() if not v.startswith("$")}
    expressions = {k: v[1:] for k, v in data.items() if v.startswith("$")}

    def create_or_update_dynamic_env(data):
        for k, v in normal.items():
            exec(f"{k} = data['{k}']")
        for k, v in expressions.items():
            exec(f"data['{k}']= {v}")

    def read_key_values(d):
        for k, v in d.items():
            s = f"""    {k}_ = input('What {k}? [{data[k]}]: ')
if {k}_ != "":
    data['{k}'] = {k}_""".strip()

            exec(s)

    create_or_update_dynamic_env(data)

    read_key_values(normal)

    create_or_update_dynamic_env(data)

    read_key_values(expressions)

    for root, dirs, files in os.walk(path):
        nroot = root[len(path) :]
        ndir = f"{destination}/{data['project_name']}{nroot}"
        template = Template(ndir)
        ndir = template.render(data)

        if not os.path.isdir(ndir):
            os.makedirs(ndir)

        for f in files:
            if f != "default.ini":
                template = Template(open(f"{root}/{f}").read())
                template_file_name = Template(f)
                f = template_file_name.render(data)
                open(f"{ndir}/{f}", "w").write(template.render(data))
