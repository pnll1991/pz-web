"""Cierre de clips por AnimSets (tools/pack/stage_txt.py) con un árbol sintético propio: nada del juego."""
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools/pack'))
import stage_txt


def anim_set(name):
    return f'<?xml version="1.0"?>\n<animNode>\n  <m_Name>n</m_Name>\n  <m_AnimName>{name}</m_AnimName>\n</animNode>\n'


class BobClipsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp = tempfile.TemporaryDirectory(prefix='pz-stage-txt-')
        cls.media = Path(cls.tmp.name) / 'media'
        sets = {
            'climbfence/start.xml': 'Bob_ClimbFence_Start', 'climbfence/nested/end.xml': 'bob_climbfence_end',
            'climbwindow/w.xml': 'Bob_ClimbWindow', 'climbrope/r.xml': 'Bob_ClimbRope', 'falling/f.xml': 'Bob_Fall',
            'melee/m.xml': 'Bob_Swing', 'idle/i.xml': 'Bob_Idle', 'climbwall/missing.xml': 'Bob_NoSuchClip',
        }
        for rel, clip in sets.items():
            f = cls.media / 'AnimSets/player' / rel
            f.parent.mkdir(parents=True, exist_ok=True)
            f.write_text(anim_set(clip))
        for clip in ('Bob_ClimbFence_Start', 'Bob_ClimbFence_End', 'Bob_ClimbWindow', 'Bob_ClimbRope', 'Bob_Fall', 'Bob_Swing', 'Bob_Idle', 'Bob_Unreferenced'):
            f = cls.media / 'anims_X/Bob' / f'{clip}.x'
            f.parent.mkdir(parents=True, exist_ok=True)
            f.write_text('x')

    @classmethod
    def tearDownClass(cls):
        cls.tmp.cleanup()

    def test_mobility_is_the_closure_of_the_movement_animsets(self):
        found, missing = stage_txt.bob_clips(self.media, 'mobility')
        self.assertEqual(sorted(Path(p).stem for p in found),
                         ['Bob_ClimbFence_End', 'Bob_ClimbFence_Start', 'Bob_ClimbRope', 'Bob_ClimbWindow', 'Bob_Fall'])
        self.assertEqual(missing, ['Bob_NoSuchClip'])  # un clip nombrado y ausente se informa, no se inventa

    def test_modes_do_not_leak_into_each_other(self):
        combat, _ = stage_txt.bob_clips(self.media, 'combat')
        self.assertEqual([Path(p).stem for p in combat], ['Bob_Swing'])
        locomotion, _ = stage_txt.bob_clips(self.media, 'locomotion')
        self.assertEqual([Path(p).stem for p in locomotion], ['Bob_Idle'])

    def test_every_mode_is_registered(self):
        self.assertEqual(set(stage_txt.ANIMSET_MODES), {'locomotion', 'combat', 'mobility'})
        for patterns in stage_txt.ANIMSET_MODES.values():
            self.assertTrue(all(p.startswith('AnimSets/player/') for p in patterns))


if __name__ == '__main__':
    unittest.main()
