import sys
import yaml
from argparse import ArgumentParser
from tqdm.auto import tqdm
import imageio
import numpy as np
from skimage.transform import resize
from skimage import img_as_ubyte
import torch
from sync_batchnorm import DataParallelWithCallback
from modules.generator import OcclusionAwareGenerator
from modules.keypoint_detector import KPDetector
from animate import normalize_kp
import ffmpeg
from os.path import splitext
from shutil import copyfileobj
from tempfile import NamedTemporaryFile
from flask import Flask, render_template, request
import random
import os
import string
import datetime
import threading
import requests
from flask import send_file
if sys.version_info[0] < 3:
    raise Exception("You must use Python 3 or higher. Recommended version is Python 3.7")
app = Flask(__name__)
def load_checkpoints(config_path, checkpoint_path, cpu=False):

    with open(config_path) as f:
        config = yaml.full_load(f)

    generator = OcclusionAwareGenerator(**config['model_params']['generator_params'],
                                        **config['model_params']['common_params'])
    if not cpu:
        generator.cuda()

    kp_detector = KPDetector(**config['model_params']['kp_detector_params'],
                             **config['model_params']['common_params'])
    if not cpu:
        kp_detector.cuda()

    if cpu:
        checkpoint = torch.load(checkpoint_path, map_location=torch.device('cpu'))
    else:
        checkpoint = torch.load(checkpoint_path)

    generator.load_state_dict(checkpoint['generator'])
    kp_detector.load_state_dict(checkpoint['kp_detector'])

    if not cpu:
        generator = DataParallelWithCallback(generator)
        kp_detector = DataParallelWithCallback(kp_detector)

    generator.eval()
    kp_detector.eval()

    return generator, kp_detector


def make_animation(source_image, driving_video, generator, kp_detector, relative=True, adapt_movement_scale=True, cpu=False):
    with torch.no_grad():
        predictions = []
        source = torch.tensor(source_image[np.newaxis].astype(np.float32)).permute(0, 3, 1, 2)
        if not cpu:
            source = source.cuda()
        driving = torch.tensor(np.array(driving_video)[np.newaxis].astype(np.float32)).permute(0, 4, 1, 2, 3)
        kp_source = kp_detector(source)
        kp_driving_initial = kp_detector(driving[:, :, 0])

        for frame_idx in tqdm(range(driving.shape[2])):
            driving_frame = driving[:, :, frame_idx]
            if not cpu:
                driving_frame = driving_frame.cuda()
            kp_driving = kp_detector(driving_frame)
            kp_norm = normalize_kp(kp_source=kp_source, kp_driving=kp_driving,
                                   kp_driving_initial=kp_driving_initial, use_relative_movement=relative,
                                   use_relative_jacobian=relative, adapt_movement_scale=adapt_movement_scale)
            out = generator(source, kp_source=kp_source, kp_driving=kp_norm)

            predictions.append(np.transpose(out['prediction'].data.cpu().numpy(), [0, 2, 3, 1])[0])
    return predictions

def find_best_frame(source, driving, cpu=False):
    import face_alignment  # type: ignore (local file)
    from scipy.spatial import ConvexHull

    def normalize_kp(kp):
        kp = kp - kp.mean(axis=0, keepdims=True)
        area = ConvexHull(kp[:, :2]).volume
        area = np.sqrt(area)
        kp[:, :2] = kp[:, :2] / area
        return kp

    fa = face_alignment.FaceAlignment(face_alignment.LandmarksType._2D, flip_input=True,
                                      device='cpu' if cpu else 'cuda')
    kp_source = fa.get_landmarks(255 * source)[0]
    kp_source = normalize_kp(kp_source)
    norm  = float('inf')
    frame_num = 0
    for i, image in tqdm(enumerate(driving)):
        kp_driving = fa.get_landmarks(255 * image)[0]
        kp_driving = normalize_kp(kp_driving)
        new_norm = (np.abs(kp_source - kp_driving) ** 2).sum()
        if new_norm < norm:
            norm = new_norm
            frame_num = i
    return frame_num

job_index = 0
generator, kp_detector = load_checkpoints(config_path='config/vox-adv-256.yaml', checkpoint_path='vox-cpk.pth.tar', cpu=False)
jobs = {}
deadjobs = []
def generateKey():
    char_set = string.ascii_lowercase + string.digits
    return ''.join(random.sample(char_set*18, 18))
def getDateTime():
    global job_index
    result = str(datetime.datetime.now()).split('.')[0]
    result = result.replace(' ', '_')
    result = result.replace(':', '-')
    result += '_' + str(job_index)
    return result

def processTicket(ticket, jobtype):
    global jobs
    global deadjobs
    print('hi')
    if not ticket in jobs:
        return
    print('hi2')
    path = 'requests/' + jobs[ticket]
    source_image = imageio.imread(path + '/src.png')
    driving_video = imageio.mimread(path + '/drv.mp4')
    source_image = resize(source_image, (256, 256))[..., :3]
    driving_video = [resize(frame, (256,256))[..., :3] for frame in driving_video]
    predictions = make_animation(source_image, driving_video, generator, kp_detector, relative = True, adapt_movement_scale=True)
    imageio.mimsave(path + '/result.mp4', [img_as_ubyte(frame) for frame in predictions], fps=30)
    deadjobs.append(ticket)
    
@app.route('/make_ticket', methods=['POST'])
def make_ticket():
    global job_index
    global jobs
    job_index = job_index + 1
    session_name = getDateTime()
    path = 'requests/' + session_name
    os.mkdir(path)
    f_1 = request.files['src_image']
    f_2 = request.files['drv_video']
    f_1.save(path + '/src.png')
    f_2.save(path + '/drv.mp4')
    key = generateKey()
    jobs[key] = session_name
    tWorker = threading.Thread(target=processTicket, args=(str(key), 1))
    tWorker.start()
    return key

@app.route('/get_animation', methods=['POST'])
def get_animation():
    global deadjobs
    ticket = request.form['ticket']
    if not ticket in ticket:
        return ''
    path = 'requests/' + jobs[ticket]
    if os.path.isfile(path + '/result.mp4')
        return send_file(path + '/result.mp4')
    return ''

if __name__ == "__main__":
    app.run(host='0.0.0.0', port=int(80), threaded=True, debug=False)
    

