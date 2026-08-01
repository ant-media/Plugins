import importlib.util
import os
import sys

# Layout on server (see copy_files.sh):
#   PythonPluginFiles/python_plugin.py, plugin_base.py, ...  (from app/*.py)
#   PythonPluginFiles/samples/init_plugins.py  (this file)
#   Each subfolder may contain init.py with init_plugin(register_plugin, callbacks).

_here = os.path.dirname(os.path.abspath(__file__))
_parent = os.path.dirname(_here)


def _iter_sample_dir_names():
    """Yield names of each immediate child directory of samples/ (sorted)."""
    try:
        for name in sorted(os.listdir(_here)):
            if name.startswith(("_", ".")) or name == "__pycache__":
                continue
            if os.path.isdir(os.path.join(_here, name)):
                yield name
    except OSError as e:
        print("init_plugins: cannot list %s: %s" % (_here, e))


# Same insert order as before: parent, each sample subdir (sorted), then samples root.
for _p in (_parent,) + tuple(os.path.join(_here, n) for n in _iter_sample_dir_names()) + (_here,):
    if _p not in sys.path:
        sys.path.insert(0, _p)


def _load_sample_init(sample_dir_name):
    init_path = os.path.join(_here, sample_dir_name, "init.py")
    if not os.path.isfile(init_path):
        return None
    module_name = "samples_%s_init" % sample_dir_name.replace(".", "_")
    spec = importlib.util.spec_from_file_location(module_name, init_path)
    if spec is None or spec.loader is None:
        print("init_plugins: cannot load spec for %s" % init_path)
        return None
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    fn = getattr(mod, "init_plugin", None)
    if fn is None:
        print("init_plugins: %s must define init_plugin(register_plugin, callbacks)" % init_path)
        return None
    return fn


def init_plugins(register_plugin, callbacks):
    """Discover each immediate subfolder of samples/ that contains init.py and call init_plugin()."""
    for name in _iter_sample_dir_names():
        sub = os.path.join(_here, name)
        init_py = os.path.join(sub, "init.py")
        if not os.path.isfile(init_py):
            continue
        init_fn = _load_sample_init(name)
        if init_fn is None:
            continue
        try:
            init_fn(register_plugin, callbacks)
            print("Python sample loaded: %s" % name)
        except Exception as e:
            print("Error in %s init_plugin: %s" % (name, e))
