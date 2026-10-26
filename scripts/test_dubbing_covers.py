import importlib.util
from pathlib import Path
import tempfile
import unittest

spec=importlib.util.spec_from_file_location('covers',Path(__file__).with_name('generate-dubbing-covers.py'))
covers=importlib.util.module_from_spec(spec);spec.loader.exec_module(covers)

class CoverImportTest(unittest.TestCase):
    def test_repeat_import_verifies_both_approved_assets(self):
        with tempfile.TemporaryDirectory() as temp:
            covers.import_covers(temp);covers.import_covers(temp)
            self.assertEqual(len(list(Path(temp).glob('*.jpg'))),2)
    def test_mismatched_target_and_source_are_never_overwritten(self):
        with tempfile.TemporaryDirectory() as temp:
            source=Path(temp)/'source';target=Path(temp)/'target'
            source.write_bytes(b'approved');target.write_bytes(b'operator')
            with self.assertRaises(ValueError):covers.atomic_import(source,target,covers.digest(source))
            self.assertEqual(target.read_bytes(),b'operator')
            with self.assertRaises(ValueError):covers.atomic_import(source,Path(temp)/'missing','bad-hash')
            self.assertFalse((Path(temp)/'missing').exists())

if __name__=='__main__':unittest.main()
