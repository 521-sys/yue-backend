-- Run only after verified image import; preserve all existing operator covers.
UPDATE dubbing_materials SET cover_key='seed-edd2913295e38d2e',cover_original_name='pendatang-arrival.jpg',cover_file_name='pendatang-arrival.jpg',cover_content_type='image/jpeg',cover_size_bytes=78621,cover_width=1280,cover_height=720,cover_version=1,version=version+1,updated_at=NOW(6)
WHERE code='pendatang-arrival' AND cover_file_name IS NULL;
UPDATE dubbing_materials SET cover_key='seed-f73457ec4ac9b3e5',cover_original_name='sfdph-wash-hands-cantonese.jpg',cover_file_name='sfdph-wash-hands-cantonese.jpg',cover_content_type='image/jpeg',cover_size_bytes=55526,cover_width=1280,cover_height=720,cover_version=1,version=version+1,updated_at=NOW(6)
WHERE code='sfdph-wash-hands-cantonese' AND cover_file_name IS NULL;
