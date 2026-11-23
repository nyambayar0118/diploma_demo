"""Газрын ангиллууд болон тэдгээрийн OSM шошгын харгалзуулалт (нэг эх сурвалж).

`python data/categories.py`-г ажиллуулж backend/src/main/resources/db/migration/V3__seed_categories.sql-г
дахин үүсгэнэ.
АНХААР: V3 өгөгдлийн санд аль хэдийн хэрэгжсэн тул энэ жагсаалтыг ӨӨРЧЛӨХГҮЙ (өөрчилбөл V3-ийн
checksum өөрчлөгдөж Flyway эхлэхгүй). Дараагийн өөрчлөлтүүд тусдаа шилжилт болно:
  V4__more_category_mappings.sql  tourism=artwork -> historic; санал болгохгүй шинэ
                                  "beauty" (shop=beauty, shop=hairdresser) ангилал wellness дор
"""
from pathlib import Path

CATEGORIES = [
    # (үндсэн код, name_mn, name_en, [(дэд код, name_mn, name_en, санал болгох эсэх, [osm шошго])])
    ("food", "Хоол, ундаа", "Food & drink", [
        ("cafe", "Кафе", "Cafe", True, ["amenity=cafe"]),
        ("restaurant", "Ресторан", "Restaurant", True, ["amenity=restaurant"]),
        ("fast_food", "Түргэн хоол", "Fast food", True, ["amenity=fast_food", "amenity=food_court"]),
        ("pub_bar", "Паб, баар", "Pub & bar", True, ["amenity=pub", "amenity=bar", "amenity=biergarten"]),
        ("dessert", "Амттан, нарийн боов", "Dessert & bakery", True,
         ["shop=bakery", "shop=confectionery", "shop=pastry", "amenity=ice_cream"]),
    ]),
    ("entertainment", "Зугаа цэнгэл", "Entertainment", [
        ("cinema", "Кино театр", "Cinema", True, ["amenity=cinema"]),
        ("game_center", "Тоглоомын төв", "Game centre", True,
         ["leisure=amusement_arcade", "leisure=bowling_alley", "leisure=escape_game", "amenity=internet_cafe"]),
        ("karaoke", "Караоке", "Karaoke", True, ["amenity=karaoke_box"]),
        ("nightclub", "Шөнийн цэнгээний газар", "Nightclub", True, ["amenity=nightclub"]),
        ("theme_park", "Зугаалгын парк", "Theme & water park", True, ["tourism=theme_park", "leisure=water_park"]),
        ("zoo", "Амьтны хүрээлэн", "Zoo & aquarium", True, ["tourism=zoo", "tourism=aquarium"]),
    ]),
    ("culture", "Соёл, урлаг", "Culture & art", [
        ("museum", "Музей", "Museum", True, ["tourism=museum"]),
        ("gallery", "Галерей", "Gallery", True, ["tourism=gallery"]),
        ("theatre", "Театр, концерт", "Theatre & concerts", True,
         ["amenity=theatre", "amenity=arts_centre", "amenity=music_venue"]),
        ("library", "Номын сан", "Library", True, ["amenity=library"]),
        ("historic", "Түүх, дурсгалт газар", "Historic site", True,
         ["historic=monument", "historic=memorial", "tourism=attraction"]),
        ("worship", "Сүм хийд", "Place of worship", True, ["amenity=place_of_worship"]),
    ]),
    ("nature", "Байгаль, гадаа", "Nature & outdoors", [
        ("park", "Цэцэрлэгт хүрээлэн", "Park & garden", True, ["leisure=park", "leisure=garden"]),
        ("scenic", "Байгалийн үзэсгэлэнт газар", "Scenic spot", True,
         ["tourism=viewpoint", "natural=peak", "leisure=nature_reserve"]),
        ("picnic", "Амралт, пикник", "Picnic & camping", True, ["tourism=picnic_site", "tourism=camp_site"]),
        ("playground", "Хүүхдийн тоглоомын талбай", "Playground", False, ["leisure=playground"]),
    ]),
    ("sport", "Спорт", "Sport", [
        ("fitness", "Фитнесс", "Fitness", True, ["leisure=fitness_centre"]),
        ("sports_hall", "Спорт заал", "Sports centre", True, ["leisure=sports_centre", "leisure=stadium"]),
        ("swimming", "Усан бассейн", "Swimming pool", True, ["leisure=swimming_pool"]),
        ("ice_rink", "Мөсөн гулгуур", "Ice rink", True, ["leisure=ice_rink"]),
        ("pitch", "Спортын талбай", "Sports pitch", False, ["leisure=pitch"]),
    ]),
    ("shopping", "Худалдаа", "Shopping", [
        ("mall", "Худалдааны төв", "Shopping mall", True, ["shop=mall", "shop=department_store"]),
        ("market", "Зах", "Market", True, ["amenity=marketplace"]),
        ("bookshop", "Номын дэлгүүр", "Bookshop", True, ["shop=books"]),
        ("fashion", "Хувцас, загвар", "Clothes & shoes", True, ["shop=clothes", "shop=shoes"]),
        ("gift", "Бэлэг, гар урлал", "Gifts & crafts", True, ["shop=gift", "shop=craft"]),
    ]),
    ("wellness", "Амралт, гоо сайхан", "Wellness", [
        ("spa", "Спа, саун", "Spa & sauna", True, ["leisure=sauna", "amenity=public_bath", "shop=massage"]),
    ]),
    ("services", "Үйлчилгээ", "Services", [
        ("grocery", "Хүнсний дэлгүүр", "Grocery", False, ["shop=supermarket", "shop=convenience"]),
        ("pharmacy", "Эмийн сан", "Pharmacy", False, ["amenity=pharmacy"]),
        ("bank", "Банк, ATM", "Bank & ATM", False, ["amenity=bank", "amenity=atm"]),
        ("hospital", "Эмнэлэг", "Hospital & clinic", False, ["amenity=hospital", "amenity=clinic"]),
        ("lodging", "Буудал", "Hotel & hostel", False, ["tourism=hotel", "tourism=hostel", "tourism=guest_house"]),
    ]),
]

# Нэг OSM объект хэд хэдэн шошгод таарвал хамгийн өндөр ач холбогдолтой харгалзуулалт сонгогдоно.
KEY_PRIORITY = {"amenity": 30, "tourism": 25, "leisure": 20, "historic": 15, "shop": 10, "natural": 5}
OVERRIDES = {"tourism=attraction": 1}  # ерөнхий шошго: илүү тодорхой шошгод ялагдана


def q(s: str) -> str:
    return "'" + s.replace("'", "''") + "'"


def generate() -> str:
    out = ["-- Place categories (2 levels) and OSM tag mappings. Generated by data/categories.py.",
           "-- Main categories are never recommended themselves; their subcategories are.", ""]
    for i, (code, mn, en, _) in enumerate(CATEGORIES, 1):
        out.append(f"INSERT INTO place_categories (code, name_mn, name_en, is_recommendable, sort_order) "
                   f"VALUES ({q(code)}, {q(mn)}, {q(en)}, FALSE, {i});")
    out.append("")
    for main, _, _, subs in CATEGORIES:
        for j, (code, mn, en, rec, _) in enumerate(subs, 1):
            out.append(f"INSERT INTO place_categories (parent_id, code, name_mn, name_en, is_recommendable, sort_order) "
                       f"SELECT id, {q(code)}, {q(mn)}, {q(en)}, {str(rec).upper()}, {j} "
                       f"FROM place_categories WHERE code = {q(main)};")
    out.append("")
    for _, _, _, subs in CATEGORIES:
        for code, _, _, _, tags in subs:
            for tag in tags:
                key, value = tag.split("=")
                priority = OVERRIDES.get(tag, KEY_PRIORITY[key])
                out.append(f"INSERT INTO osm_tag_mappings (osm_key, osm_value, category_id, priority) "
                           f"SELECT {q(key)}, {q(value)}, id, {priority} FROM place_categories WHERE code = {q(code)};")
    return "\n".join(out) + "\n"


if __name__ == "__main__":
    target = Path(__file__).resolve().parent.parent / "backend/src/main/resources/db/migration/V3__seed_categories.sql"
    target.write_text(generate(), encoding="utf-8")
    print(f"wrote {target}")
